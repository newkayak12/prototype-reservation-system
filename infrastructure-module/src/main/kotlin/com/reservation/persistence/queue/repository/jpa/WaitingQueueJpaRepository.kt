package com.reservation.persistence.queue.repository.jpa

import com.reservation.persistence.queue.entity.WaitingQueueEntity
import com.reservation.persistence.queue.repository.jpa.projection.WaitingQueueSlotProjection
import jakarta.persistence.LockModeType.PESSIMISTIC_WRITE
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

interface WaitingQueueJpaRepository : CrudRepository<WaitingQueueEntity, Long> {
    companion object {
        private const val SLOT_CONDITION = """
        waitingQueue.restaurantId = :restaurantId
        AND waitingQueue.date = :date
        AND waitingQueue.startTime = :startTime
        """

        private const val COUNT_PRECEDING_SQL = """
        SELECT COUNT(waitingQueue)
        FROM WaitingQueueEntity waitingQueue
        WHERE $SLOT_CONDITION
        AND waitingQueue.status = 'WAITING'
        AND waitingQueue.id <= :id
        """

        private const val COUNT_ADMITTED_SQL = """
        SELECT COUNT(waitingQueue)
        FROM WaitingQueueEntity waitingQueue
        WHERE $SLOT_CONDITION
        AND waitingQueue.status = 'ADMITTED'
        AND waitingQueue.admittedAt >= :notExpiredAfter
        """

        private const val FIND_WAITING_SQL = """
        SELECT waitingQueue
        FROM WaitingQueueEntity waitingQueue
        WHERE $SLOT_CONDITION
        AND waitingQueue.status = 'WAITING'
        ORDER BY waitingQueue.id ASC
        """

        private const val FIND_SLOTS_SQL = """
        SELECT DISTINCT
            waitingQueue.restaurantId AS restaurantId,
            waitingQueue.date AS date,
            waitingQueue.startTime AS startTime
        FROM WaitingQueueEntity waitingQueue
        WHERE waitingQueue.status = 'WAITING'
        """
    }

    fun findByRestaurantIdAndDateAndStartTimeAndTicketId(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
        ticketId: String,
    ): WaitingQueueEntity?

    /**
     * Redis `TICKET_OF:{slot}:{userId}` 조회에 대응한다.
     * `unique_slot_user_id` 유니크 키가 있으므로 결과는 최대 한 건이다.
     */
    fun findByRestaurantIdAndDateAndStartTimeAndUserId(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
        userId: String,
    ): WaitingQueueEntity?

    @Query(COUNT_PRECEDING_SQL)
    fun countPreceding(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
        id: Long,
    ): Long

    @Query(COUNT_ADMITTED_SQL)
    fun countAdmitted(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
        notExpiredAfter: LocalDateTime,
    ): Long

    /**
     * 입장 허용 후보를 `SELECT ... FOR UPDATE`로 잠근 채 읽는다.
     *
     * 여러 인스턴스의 스케줄러가 동시에 승격을 시도하면 "몇 자리 남았는지 센다 → 그만큼
     * 승격한다" 사이에 TOCTOU가 생겨 정원을 초과한다. 후보 row를 먼저 잠그면 두 번째
     * 인스턴스는 첫 번째가 커밋할 때까지 이 지점에서 막히고, 풀려난 뒤에는 (잠금 읽기는 항상
     * 최신 커밋본을 보므로) 방금 ADMITTED로 바뀐 row를 건너뛴 상태에서 다시 센다.
     */
    @Lock(PESSIMISTIC_WRITE)
    @Query(FIND_WAITING_SQL)
    fun findWaitingForUpdate(
        restaurantId: String,
        date: LocalDate,
        startTime: LocalTime,
        pageable: Pageable,
    ): List<WaitingQueueEntity>

    @Query(FIND_SLOTS_SQL)
    fun findWaitingSlots(): List<WaitingQueueSlotProjection>
}
