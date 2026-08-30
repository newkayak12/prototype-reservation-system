package com.reservation.redis.redisson.queue.adapter

import com.reservation.enumeration.QueueStatus
import com.reservation.queue.port.output.FindTicketResult
import com.reservation.redis.redisson.queue.util.WaitingQueueKeyGenerator
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.stereotype.Component

/**
 * `RESULT:{ticketId}` 조회.
 *
 * 이 키를 쓰는 쪽(PENDING/CONFIRMED/CANCELLED 기록)은 Phase 4다. Phase 1에서는 읽기 경로만
 * 미리 뚫어 두고, 값이 없으면 호출자가 ADMITTED/WAITING 계산으로 폴백한다.
 * Redis가 죽었을 때는 DB에 대응 테이블이 아직 없으므로 "결과 없음"으로 간주한다.
 */
@Component
class FindTicketResultRedisAdapter(
    private val redissonClient: RedissonClient,
) : FindTicketResult {
    private val log = loggerFactory<FindTicketResultRedisAdapter>()

    override fun query(ticketId: String): QueueStatus? =
        try {
            redissonClient
                .getBucket<String>(WaitingQueueKeyGenerator.result(ticketId))
                .get()
                ?.let { runCatching { QueueStatus.valueOf(it) }.getOrNull() }
        } catch (exception: RedisException) {
            log.warn(
                "Unable to connect to Redis. ticket result is unavailable: {}",
                exception.message,
            )
            null
        }
}
