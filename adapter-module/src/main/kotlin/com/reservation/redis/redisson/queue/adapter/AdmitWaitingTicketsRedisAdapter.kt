package com.reservation.redis.redisson.queue.adapter

import com.reservation.queue.port.output.AdmitWaitingTickets
import com.reservation.queue.port.output.AdmitWaitingTickets.AdmitInquiry
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.redis.redisson.queue.util.WaitingQueueKeyGenerator
import com.reservation.utilities.logger.loggerFactory
import org.redisson.api.RPermitExpirableSemaphore
import org.redisson.api.RedissonClient
import org.redisson.client.RedisException
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS

/**
 * 대기열 → 입장 허용(ADMITTED) 승격.
 *
 * 순서가 중요하다: **permit을 먼저 얻고 그 다음에 ZSET을 pop** 한다.
 * 반대로 하면 permit이 없을 때 이미 대기열에서 빠져나온 티켓을 잃어버린다.
 * `pollFirst()`는 Redis 레벨에서 원자적(ZPOPMIN)이므로 워커가 여러 개여도 중복 승격되지 않는다.
 *
 * ## 왜 좌석 세마포어([com.reservation.timetable.port.output.AcquireTimeTableSemaphore])를
 * ## 재사용하지 않는가
 *
 * 좌석 세마포어는 `RSemaphore.trySetPermits(capacity, ttl)`을 쓴다. 이 호출은 **키가 이미
 * 있으면 아무것도 하지 않으므로** TTL이 "키 생성 시각"에 한 번 고정된다. permit은 오직 키가
 * 통째로 만료될 때만 한꺼번에 되돌아온다. 즉 capacity가 "동시 입장 허용치"가 아니라
 * "TTL 주기마다 초기화되는 예산(tumbling window)"으로 동작한다.
 *
 * 그 결과 TTL 경계에서 최대 2배 초과 입장이 발생한다.
 * - t=0   에 permit pool 생성(TTL 5분) → t=3분 에 capacity만큼 승격.
 *   이 티켓들의 `ADMITTED:{key}` 엔트리는 각자 t=8분까지 살아있다.
 * - t=5분 에 permit pool 키가 만료 → permit이 전부 복구 → capacity만큼 또 승격.
 * - t=5분~8분 구간에는 살아있는 ADMITTED가 2 * capacity가 된다.
 *
 * ## 대신 쓰는 것: permit 단위 lease
 *
 * [RPermitExpirableSemaphore]는 permit **하나하나**에 lease를 건다. 여기서는 lease를
 * `admissionTimeToLive`로 잡아, permit의 수명과 `ADMITTED:{key}` 엔트리의 수명을 정확히
 * 일치시킨다. permit은 "그 티켓의 입장 허용이 살아있는 동안"만 점유되고 만료와 동시에
 * 개별적으로 회수되므로, capacity가 tumbling budget이 아니라 진짜 동시 실행 한도가 된다.
 *
 * - [RPermitExpirableSemaphore.trySetPermits]는 capacity만 심고 키 TTL은 걸지 않는다.
 *   (키 TTL을 걸면 위와 똑같은 tumbling window 문제가 되돌아온다.)
 * - 대신 슬롯이 살아있는 동안 매 사이클마다 키 TTL을 `lease * 2`로 **갱신**한다.
 *   갱신 주기(워커 주기)가 lease보다 훨씬 짧으므로 permit이 물려 있는 동안 키가 사라질 일은
 *   없고, 슬롯이 완전히 잠잠해져 lease가 전부 만료된 뒤에야 키가 사라진다.
 *   (그 시점에 capacity가 full로 리셋되는 것은 올바른 동작이다.)
 *
 * 또 하나: 여기서는 `AcquireSemaphoreTemplate`을 거치지 않고 [RedissonClient]를 직접 쓴다.
 * 그 템플릿은 `runCatching {}.getOrElse { false }`로 예외를 통째로 삼키기 때문에, 사이클
 * 도중에 Redis가 죽으면 `RedisException`이 `false`로 둔갑해 DB 폴백이 아예 발동하지 않는다.
 */
@Component
class AdmitWaitingTicketsRedisAdapter(
    private val redissonClient: RedissonClient,
    private val waitingQueueFallbackCoordinator: WaitingQueueFallbackCoordinator,
) : AdmitWaitingTickets {
    companion object {
        private const val PERMIT_WAIT_TIME_MILLIS = 0L
        private const val KEY_LIVE_TIME_MULTIPLIER = 2L
    }

    private val log = loggerFactory<AdmitWaitingTicketsRedisAdapter>()

    private fun semaphore(slot: WaitingQueueSlot): RPermitExpirableSemaphore =
        redissonClient.getPermitExpirableSemaphore(WaitingQueueKeyGenerator.admission(slot))

    /**
     * 키 TTL을 갱신한다. 갱신 값이 lease보다 충분히 길어야 permit이 물려 있는 채로 키가
     * 사라지지 않는다.
     *
     * `trySetPermits`와 떼어 놓은 이유는 호출 빈도 때문이다. 이 어댑터는 이제 타이머가
     * 아니라 **폴링 요청마다** 불린다. 대기자가 3,000명이면 초당 수천 번인데, 그중 거의
     * 전부는 "자리 없음"으로 끝나는 헛걸음이다. 그 경로에서까지 쓰기를 하면 아무 일도
     * 일어나지 않는 동안 Redis에 쓰기만 쌓인다.
     *
     * 실제로 승격이 일어났을 때만 갱신해도 안전하다 — 승격이 없다는 것은 permit이 전부
     * 물려 있다는 뜻이고, 그 permit들은 늦어도 lease가 끝나면 풀린다. 키 TTL은 그 두 배라
     * 먼저 사라지지 않는다.
     */
    private fun RPermitExpirableSemaphore.refreshTimeToLive(lease: Duration) {
        expire(lease.multipliedBy(KEY_LIVE_TIME_MULTIPLIER))
    }

    /** permit 하나를 [lease] 동안만 빌린다. 남은 permit이 없으면 즉시 null. */
    private fun RPermitExpirableSemaphore.tryAcquirePermit(lease: Duration): String? =
        tryAcquire(PERMIT_WAIT_TIME_MILLIS, lease.toMillis(), MILLISECONDS)

    private fun markAdmitted(
        slot: WaitingQueueSlot,
        ticketId: String,
        permitId: String,
        timeToLive: Duration,
    ) {
        redissonClient.getSetCache<String>(WaitingQueueKeyGenerator.admitted(slot))
            .add(ticketId, timeToLive.seconds, SECONDS)

        // permitId를 버리면 permit을 되돌려 줄 방법이 사라진다. 예약을 마친 사용자의 자리가
        // lease 만료까지 묶여, 대기 중인 다음 사람이 그만큼 더 기다린다.
        // TTL을 lease와 같게 잡아 permit이 만료되는 시점에 이 인덱스도 함께 사라지게 한다.
        redissonClient.getBucket<String>(WaitingQueueKeyGenerator.permitOf(slot, ticketId))
            .set(permitId, timeToLive)
    }

    private fun admitByRedis(inquiry: AdmitInquiry): Int {
        val slot = inquiry.slot
        val timeToLive = inquiry.admissionTimeToLive
        val queue =
            redissonClient.getScoredSortedSet<String>(WaitingQueueKeyGenerator.waitingQueue(slot))

        // Redis가 죽어 있으면 이 호출이 RedisException을 던져 DB 폴백으로 넘어간다.
        if (queue.isEmpty) return 0

        val semaphore = semaphore(slot)
        semaphore.trySetPermits(inquiry.capacity)

        var admitted = 0
        var permitId = semaphore.tryAcquirePermit(timeToLive)
        while (permitId != null) {
            val ticketId = queue.pollFirst()
            if (ticketId == null) {
                semaphore.tryRelease(permitId)
                break
            }

            markAdmitted(slot, ticketId, permitId, timeToLive)
            admitted++
            permitId = semaphore.tryAcquirePermit(timeToLive)
        }

        if (admitted > 0) semaphore.refreshTimeToLive(timeToLive)

        return admitted
    }

    override fun admit(inquiry: AdmitInquiry): Int =
        try {
            admitByRedis(inquiry)
        } catch (exception: RedisException) {
            log.warn("Unable to connect to Redis. fall back to database: {}", exception.message)
            waitingQueueFallbackCoordinator.admit(
                inquiry.slot,
                inquiry.capacity,
                inquiry.admissionTimeToLive,
            )
        }
}
