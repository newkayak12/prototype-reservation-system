package com.reservation.persistence.queue.repository.adapter

import com.reservation.enumeration.QueueStatus.ADMITTED
import com.reservation.enumeration.QueueStatus.WAITING
import com.reservation.persistence.queue.entity.WaitingQueueEntity
import com.reservation.persistence.queue.repository.jpa.WaitingQueueJpaRepository
import com.reservation.queue.port.output.EnterWaitingQueue
import com.reservation.queue.port.output.EnterWaitingQueue.Companion.ADMITTED_POSITION
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.queue.vo.WaitingQueueSlot
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

/**
 * Redis 장애 시의 대기열 좌표계 구현.
 *
 * Redis 자료구조와의 대응:
 * - `INCR SEQUENCE:{key}` → auto-increment `waiting_queue.id`
 * - `ZADD WAITING_QUEUE:{key}` → `status = 'WAITING'` row insert
 * - `ZRANK` → 같은 슬롯에서 자기 id 이하인 WAITING row 수
 * - `SADD ADMITTED:{key}` + TTL → `status = 'ADMITTED'` + `admitted_at`
 */
@Component
class WaitingQueueDatabaseAdapter(
    private val waitingQueueJpaRepository: WaitingQueueJpaRepository,
) : WaitingQueueFallbackCoordinator {
    companion object {
        private const val FIRST_PAGE = 0
        private const val NO_VACANCY = 0
    }

    private fun find(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): WaitingQueueEntity? =
        waitingQueueJpaRepository.findByRestaurantIdAndDateAndStartTimeAndTicketId(
            restaurantId = slot.restaurantId,
            date = slot.date,
            startTime = slot.startTime,
            ticketId = ticketId,
        )

    private fun findByUser(
        slot: WaitingQueueSlot,
        userId: String,
    ): WaitingQueueEntity? =
        waitingQueueJpaRepository.findByRestaurantIdAndDateAndStartTimeAndUserId(
            restaurantId = slot.restaurantId,
            date = slot.date,
            startTime = slot.startTime,
            userId = userId,
        )

    private fun position(entity: WaitingQueueEntity): Long =
        waitingQueueJpaRepository.countPreceding(
            restaurantId = entity.restaurantId,
            date = entity.date,
            startTime = entity.startTime,
            id = entity.id!!,
        )

    /**
     * 진입의 멱등 기준은 티켓이 아니라 **사용자**다 — ticketId가 서버 발급 nonce라 재요청마다
     * 값이 달라지므로, 티켓으로 찾으면 같은 사용자가 매번 새 row를 만들어 대기열에 여러 자리를
     * 잡는다. Redis 경로의 `TICKET_OF` 선점에 대응하는 것이 `unique_slot_user_id`다.
     */
    @Transactional
    override fun enter(
        slot: WaitingQueueSlot,
        userId: String,
        ticketId: String,
    ): EnterWaitingQueue.EnteredTicket {
        val found = findByUser(slot, userId)

        // 이미 ADMITTED 된 티켓은 대기열로 되돌리지 않는다. countPreceding은 WAITING만 세므로
        // 이 분기가 없으면 ADMITTED 재진입자에게 엉뚱한(자기 앞의 대기자 수) 순번이 나간다.
        if (found?.status == ADMITTED) {
            return EnterWaitingQueue.EnteredTicket(found.ticketId, ADMITTED_POSITION)
        }

        val entity =
            found
                ?: waitingQueueJpaRepository.save(
                    WaitingQueueEntity(
                        restaurantId = slot.restaurantId,
                        date = slot.date,
                        startTime = slot.startTime,
                        userId = userId,
                        ticketId = ticketId,
                    ),
                )

        // 먼저 들어온 요청이 심어 둔 티켓이 있으면 이번 후보가 아니라 그쪽이 정답이다.
        return EnterWaitingQueue.EnteredTicket(entity.ticketId, position(entity))
    }

    @Transactional(readOnly = true)
    override fun isUserAdmitted(
        slot: WaitingQueueSlot,
        userId: String,
    ): Boolean = findByUser(slot, userId)?.status == ADMITTED

    @Transactional(readOnly = true)
    override fun findPosition(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): Long? =
        find(slot, ticketId)
            ?.takeIf { it.status == WAITING }
            ?.let { position(it) }

    @Transactional(readOnly = true)
    override fun isAdmitted(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): Boolean = find(slot, ticketId)?.status == ADMITTED

    /**
     * Redis 폴백 승격. 인스턴스가 여러 대여도 정원을 넘기지 않아야 한다.
     *
     * 순서가 중요하다.
     * 1. 후보를 **먼저 `FOR UPDATE`로 잠근다.** 잠그는 개수는 이번 사이클에 승격할 수 있는
     *    최대치인 `capacity`다. 두 인스턴스가 동시에 들어오면 둘 다 대기열 맨 앞 row를
     *    노리므로 여기서 직렬화된다.
     * 2. 그 다음에 살아있는 ADMITTED 수를 센다. InnoDB의 consistent read view는 트랜잭션의
     *    첫 **비잠금** 읽기에서 만들어지므로, 잠금 읽기 뒤에 오는 이 COUNT는 앞선 인스턴스가
     *    커밋한 승격까지 반영한 최신 값을 본다.
     * 3. 남은 자리만큼만, 이미 잠가 둔 후보 앞에서부터 승격한다.
     */
    @Transactional
    override fun admit(
        slot: WaitingQueueSlot,
        capacity: Int,
        admissionTimeToLive: Duration,
    ): Int {
        val now = LocalDateTime.now()
        val candidates =
            waitingQueueJpaRepository.findWaitingForUpdate(
                restaurantId = slot.restaurantId,
                date = slot.date,
                startTime = slot.startTime,
                pageable = PageRequest.of(FIRST_PAGE, capacity),
            )
        if (candidates.isEmpty()) return 0

        val admittedCount =
            waitingQueueJpaRepository.countAdmitted(
                restaurantId = slot.restaurantId,
                date = slot.date,
                startTime = slot.startTime,
                notExpiredAfter = now.minus(admissionTimeToLive),
            )
        // 정원이 이미 찼으면 vacancy가 음수일 수 있다. take는 음수를 받지 않으므로 0으로 깎는다.
        val vacancy = (capacity - admittedCount.toInt()).coerceAtLeast(NO_VACANCY)
        val targets = candidates.take(vacancy)

        // 승격 대상이 없으면 쓰기 자체를 하지 않는다. 정원이 꽉 찬 슬롯은 워커가 매 사이클 훑으므로
        // 빈 saveAll을 그대로 흘려보내면 아무 일도 안 하는 쓰기 트랜잭션이 주기적으로 쌓인다.
        if (targets.isNotEmpty()) {
            targets.forEach { it.admit(now) }
            waitingQueueJpaRepository.saveAll(targets)
        }

        return targets.size
    }

    @Transactional(readOnly = true)
    override fun loadSlots(): List<WaitingQueueSlot> =
        waitingQueueJpaRepository.findWaitingSlots()
            .map {
                WaitingQueueSlot(
                    restaurantId = it.getRestaurantId(),
                    date = it.getDate(),
                    startTime = it.getStartTime(),
                )
            }
}
