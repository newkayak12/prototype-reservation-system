package com.reservation.redis.redisson.queue.adapter

import com.reservation.queue.port.output.FindWaitingTicketPosition
import com.reservation.queue.port.output.FindWaitingTicketPosition.WaitingTicketInquiry
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.redis.redisson.queue.util.WaitingQueueKeyGenerator
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.stereotype.Component

/**
 * `ZRANK WAITING_QUEUE:{key} {ticketId}` → 1-base 순번.
 */
@Component
class FindWaitingTicketPositionRedisAdapter(
    private val redissonClient: RedissonClient,
    private val waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
) : FindWaitingTicketPosition {
    private val log = loggerFactory<FindWaitingTicketPositionRedisAdapter>()

    override fun query(inquiry: WaitingTicketInquiry): Long? =
        try {
            redissonClient
                .getScoredSortedSet<String>(
                    WaitingQueueKeyGenerator.waitingQueue(inquiry.slot),
                )
                .rank(inquiry.ticketId)
                ?.let { it.toLong() + 1 }
        } catch (exception: RedisException) {
            log.warn("Unable to connect to Redis. fall back to database: {}", exception.message)
            waitingQueueFallbackCoordinator.findPosition(inquiry.slot, inquiry.ticketId)
        }
}
