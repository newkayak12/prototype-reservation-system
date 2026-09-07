package com.reservation.redis.redisson.queue.adapter

import com.reservation.queue.port.output.ReleaseAdmission
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.redis.redisson.queue.util.WaitingQueueKeyGenerator
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.stereotype.Component

/**
 * 입장 자리 반납.
 *
 * `TICKET_OF` → `PERMIT_OF` 두 단계를 거친다. 클라이언트가 보낸 티켓을 믿지 않고 인증된
 * `userId`에서 출발해 서버가 직접 티켓을 되찾아오기 때문이다 — 예약 게이트와 같은 이유이고,
 * 여기서는 더 중요하다. 남의 티켓 ID로 이 경로를 부르면 그 사람의 입장 자격을 뺏을 수 있다.
 *
 * 정리하는 것은 셋이다.
 * - permit 자체(`tryRelease`) — 이걸 해야 대기 중인 다음 사람이 들어온다.
 * - `PERMIT_OF` 인덱스 — 남겨 두면 이미 반납한 permit을 또 반납하려 든다.
 * - `ADMITTED` 엔트리 — 입장 자격의 근거라서, 남겨 두면 자리는 반납했는데 자격은 남는다.
 */
@Component
class ReleaseAdmissionRedisAdapter(
    private val redissonClient: RedissonClient,
) : ReleaseAdmission {
    private val log = loggerFactory<ReleaseAdmissionRedisAdapter>()

    override fun release(
        slot: WaitingQueueSlot,
        userId: String,
    ) {
        try {
            releaseByRedis(slot, userId)
        } catch (exception: RedisException) {
            // lease가 만료되며 결국 회수된다. 정리 실패로 이미 성공한 예약을 뒤집지 않는다.
            log.warn(
                "Failed to release the admission. it will be reclaimed by lease: {}",
                exception.message,
            )
        }
    }

    private fun releaseByRedis(
        slot: WaitingQueueSlot,
        userId: String,
    ) {
        val ticketId =
            redissonClient.getBucket<String>(
                WaitingQueueKeyGenerator.ticketOf(slot, userId),
            ).get() ?: return

        val permitBucket =
            redissonClient.getBucket<String>(WaitingQueueKeyGenerator.permitOf(slot, ticketId))
        val permitId = permitBucket.andDelete

        redissonClient.getSetCache<String>(WaitingQueueKeyGenerator.admitted(slot))
            .remove(ticketId)

        if (permitId == null) return

        redissonClient.getPermitExpirableSemaphore(WaitingQueueKeyGenerator.admission(slot))
            .tryRelease(permitId)
    }
}
