package com.reservation.redis.redisson.queue.adapter

import com.reservation.queue.port.output.IsUserAdmitted
import com.reservation.queue.port.output.IsUserAdmitted.UserAdmissionInquiry
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.redis.redisson.queue.util.WaitingQueueKeyGenerator
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.stereotype.Component

/**
 * `TICKET_OF:{key}:{userId}` → `ADMITTED:{key}` 2단 조회.
 *
 * 티켓을 쥔 적이 없으면(= 대기열을 통과하지 않았으면) 첫 단계에서 바로 걸러진다.
 */
@Component
class IsUserAdmittedRedisAdapter(
    private val redissonClient: RedissonClient,
    private val waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
) : IsUserAdmitted {
    private val log = loggerFactory<IsUserAdmittedRedisAdapter>()

    override fun query(inquiry: UserAdmissionInquiry): Boolean =
        try {
            val ticketId =
                redissonClient
                    .getBucket<String>(
                        WaitingQueueKeyGenerator.ticketOf(inquiry.slot, inquiry.userId),
                    )
                    .get()

            ticketId != null &&
                redissonClient
                    .getSetCache<String>(WaitingQueueKeyGenerator.admitted(inquiry.slot))
                    .contains(ticketId)
        } catch (exception: RedisException) {
            log.warn("Unable to connect to Redis. fall back to database: {}", exception.message)
            waitingQueueFallbackCoordinator.isUserAdmitted(inquiry.slot, inquiry.userId)
        }
}
