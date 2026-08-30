package com.reservation.redis.redisson.queue.adapter

import com.reservation.queue.port.output.IsTicketAdmitted
import com.reservation.queue.port.output.IsTicketAdmitted.AdmissionInquiry
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.redis.redisson.queue.util.WaitingQueueKeyGenerator
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.stereotype.Component

/**
 * `ADMITTED:{key}` SET(TTL 있음) 조회. 대기열 폴링과 예약 강제 게이트가 공유한다.
 */
@Component
class IsTicketAdmittedRedisAdapter(
    private val redissonClient: RedissonClient,
    private val waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
) : IsTicketAdmitted {
    private val log = loggerFactory<IsTicketAdmittedRedisAdapter>()

    override fun query(inquiry: AdmissionInquiry): Boolean =
        try {
            redissonClient
                .getSetCache<String>(WaitingQueueKeyGenerator.admitted(inquiry.slot))
                .contains(inquiry.ticketId)
        } catch (exception: RedisException) {
            log.warn("Unable to connect to Redis. fall back to database: {}", exception.message)
            waitingQueueFallbackCoordinator.isAdmitted(inquiry.slot, inquiry.ticketId)
        }
}
