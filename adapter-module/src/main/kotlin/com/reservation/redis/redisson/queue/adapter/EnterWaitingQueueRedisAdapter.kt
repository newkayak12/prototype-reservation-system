package com.reservation.redis.redisson.queue.adapter

import com.reservation.queue.port.output.EnterWaitingQueue
import com.reservation.queue.port.output.EnterWaitingQueue.Companion.ADMITTED_POSITION
import com.reservation.queue.port.output.EnterWaitingQueue.EnterWaitingQueueInquiry
import com.reservation.queue.port.output.EnterWaitingQueue.EnteredTicket
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.redis.redisson.queue.util.WaitingQueueKeyGenerator
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit.HOURS

/**
 * `INCR SEQUENCE:{key}`로 시퀀스를 뽑아 `WAITING_QUEUE:{key}` ZSET에 티켓을 넣는다.
 *
 * `DistributedLockAspect`가 `RedisException`을 만나면 `NamedLockCoordinator`로 갈아타듯,
 * 여기서도 `RedisException`을 잡아 DB(`waiting_queue`) 좌표계로 전환한다.
 */
@Component
class EnterWaitingQueueRedisAdapter(
    private val redissonClient: RedissonClient,
    private val waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
    @Value("\${reservation.queue.ticket-time-to-live-seconds:1800}")
    private val ticketTimeToLiveSeconds: Long,
) : EnterWaitingQueue {
    companion object {
        private const val SLOT_LIVE_TIME = 1L
        private const val FIRST_POSITION = 0
    }

    private val log = loggerFactory<EnterWaitingQueueRedisAdapter>()

    private fun registerSlot(slot: WaitingQueueSlot) {
        redissonClient.getSetCache<String>(WaitingQueueKeyGenerator.SLOTS)
            .add(slot.key(), SLOT_LIVE_TIME, HOURS)
    }

    private fun isAdmitted(
        slot: WaitingQueueSlot,
        ticketId: String,
    ): Boolean =
        redissonClient.getSetCache<String>(WaitingQueueKeyGenerator.admitted(slot))
            .contains(ticketId)

    /**
     * 이 사용자가 이 슬롯에서 실제로 쥐는 티켓을 확정한다.
     *
     * `ticketId`는 서버 발급 nonce라 재요청마다 값이 다르다. 그대로 두면 사용자가 진입을
     * 반복 호출하는 것만으로 대기열에 여러 자리를 잡고 나중에 입장 정원을 여러 번 소모한다.
     * 그래서 멱등성의 기준을 티켓이 아니라 **사용자**로 옮긴다.
     *
     * `SET NX`는 원자적이므로 동시에 들어온 두 요청 중 정확히 하나만 자기 nonce를 심는다.
     * 진 쪽은 `get()`으로 이긴 nonce를 그대로 돌려받으므로, 둘 다 같은 티켓으로 수렴한다.
     * (락이 필요 없는 이유가 이것이다 — 경합의 승패를 Redis의 단일 커맨드가 정해 준다.)
     */
    private fun electTicket(
        slot: WaitingQueueSlot,
        userId: String,
        candidate: String,
    ): String {
        val bucket =
            redissonClient.getBucket<String>(
                WaitingQueueKeyGenerator.ticketOf(slot, userId),
            )
        val duration = Duration.ofSeconds(ticketTimeToLiveSeconds)

        // 선점에 성공하면 내 후보가 곧 결과다. 실패했다면 먼저 심어진 값을 읽어 온다.
        // 읽는 사이 TTL로 사라졌다면 이번엔 내 후보로 다시 선점을 시도한다.
        return if (bucket.setIfAbsent(candidate, duration)) {
            candidate
        } else {
            bucket.get() ?: candidate.also { bucket.set(it, duration) }
        }
    }

    private fun enterByRedis(inquiry: EnterWaitingQueueInquiry): EnteredTicket {
        val slot = inquiry.slot
        val ticketId = electTicket(slot, inquiry.userId, inquiry.ticketId)
        val queue =
            redissonClient.getScoredSortedSet<String>(WaitingQueueKeyGenerator.waitingQueue(slot))

        // ADMITTED 된 티켓은 이미 ZPOPMIN 되어 rank가 null이다. 이 분기가 없으면 재진입한
        // 사용자가 대기열 맨 뒤에 다시 붙어 나중에 permit을 한 번 더 소모하게 된다.
        if (isAdmitted(slot, ticketId)) return EnteredTicket(ticketId, ADMITTED_POSITION)

        // 같은 티켓으로 다시 진입해도 시퀀스를 새로 소모하지 않고 기존 순번을 그대로 돌려준다.
        val rank =
            queue.rank(ticketId)
                ?: run {
                    val sequence =
                        redissonClient.getAtomicLong(WaitingQueueKeyGenerator.sequence(slot))
                            .incrementAndGet()
                    queue.addIfAbsent(sequence.toDouble(), ticketId)
                    queue.rank(ticketId) ?: FIRST_POSITION
                }

        registerSlot(slot)

        return EnteredTicket(ticketId, rank.toLong() + 1)
    }

    override fun enter(inquiry: EnterWaitingQueueInquiry): EnteredTicket =
        try {
            enterByRedis(inquiry)
        } catch (exception: RedisException) {
            log.warn("Unable to connect to Redis. fall back to database: {}", exception.message)
            waitingQueueFallbackCoordinator.enter(
                inquiry.slot,
                inquiry.userId,
                inquiry.ticketId,
            )
        }
}
