package com.reservation.redis.redisson.queue.adapter

import com.reservation.queue.port.output.LoadWaitingQueueSlots
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.redis.redisson.queue.util.WaitingQueueKeyGenerator
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.stereotype.Component

/**
 * 대기열이 살아있는 슬롯 목록. 진입할 때마다 TTL이 갱신되는 `QUEUE_SLOTS` SET을 읽는다.
 */
@Component
class LoadWaitingQueueSlotsRedisAdapter(
    private val redissonClient: RedissonClient,
    private val waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
) : LoadWaitingQueueSlots {
    private val log = loggerFactory<LoadWaitingQueueSlotsRedisAdapter>()

    override fun query(): List<WaitingQueueSlot> =
        try {
            redissonClient.getSetCache<String>(WaitingQueueKeyGenerator.SLOTS)
                .readAll()
                .mapNotNull { runCatching { WaitingQueueSlot.from(it) }.getOrNull() }
        } catch (exception: RedisException) {
            log.warn("Unable to connect to Redis. fall back to database: {}", exception.message)
            waitingQueueFallbackCoordinator.loadSlots()
        }
}
