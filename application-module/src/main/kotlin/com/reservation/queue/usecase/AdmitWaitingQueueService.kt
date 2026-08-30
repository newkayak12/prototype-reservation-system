package com.reservation.queue.usecase

import com.reservation.config.annotations.DistributedLock
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.LockType.LOCK
import com.reservation.queue.port.input.AdmitSlotUseCase
import com.reservation.queue.port.input.AdmitWaitingQueueUseCase
import com.reservation.queue.port.output.LoadWaitingQueueSlots
import java.util.concurrent.TimeUnit.MILLISECONDS

/**
 * 대기열 리컨실러의 한 사이클.
 *
 * ## 이건 본 펌프가 아니다
 *
 * 큐를 실제로 미는 것은 사용자 요청이다 — 진입([EnterWaitingQueueService])과 폴링
 * ([InquiryWaitingTicketService])이 각각 자기 슬롯에 대해 [AdmitSlotUseCase]를 부른다.
 * permit이 풀리는 계기는 결국 "누가 도착했다"와 "누가 반납했다" 둘뿐이고, 둘 다 요청이라
 * 그 자리에서 처리하면 큐는 항상 움직인다.
 *
 * 이 워커를 본 펌프로 쓰면 주기가 곧 처리량 상한이 된다. 실제로 그랬다 —
 * `capacity 100 ÷ 500ms = 200 req/s`에 실측 처리율이 그대로 붙어 있었고, VU를 100에서
 * 3,000까지 30배 올려도 움직이지 않았다. 부하가 없을 때조차 첫 사용자가 한 주기를
 * 기다렸다.
 *
 * ## 그럼 왜 남겨 두는가 — 이벤트가 오지 않는 경우들
 *
 * - **lease 만료.** 입장 허용을 받고 예약하지 않은 사용자(창을 닫은 경우)의 permit은
 *   Redis가 lease 만료로 회수하는데, 이건 Redis 내부의 수동적 만료라 "다음 사람을 뽑아라"를
 *   호출해 줄 주체가 없다. 발생하는 이벤트 자체가 없다.
 * - **홀드 만료로 좌석 복귀.** `TimeTableHoldExpiryScheduler`가 PENDING 홀드를 풀면 좌석이
 *   생기지만 대기열 쪽에는 아무 신호도 오지 않는다.
 * - **반납 직후 인스턴스 종료.** 그 승격이 유실된다.
 *
 * 셋 다 "아무도 부르지 않는데 자리가 비는" 상황이고, 그때만 이 워커가 필요하다. 그래서
 * 주기는 응답성이 아니라 **유실 복구 지연 허용치**로 정한다 — 초 단위면 충분하고,
 * 짧게 잡을수록 살아있는 슬롯 전수 조회 비용만 늘어난다.
 *
 * ## 한 번에 한 인스턴스만
 *
 * Redis 경로의 승격은 그 자체로 원자적이라 락이 없어도 정원을 넘기지 않는다. 락이 필요한
 * 것은 Redis가 죽어 DB 폴백으로 내려갔을 때다 — "센다 → 승격한다"가 여러 인스턴스에서
 * 겹치면 정원을 넘길 수 있다.
 *
 * ShedLock을 새로 들이는 대신 이미 이 저장소에 있는 [DistributedLock]을 쓴다.
 * `DistributedLockAspect`가 Redis 락을 먼저 잡고 `RedisException`이면 `NamedLockCoordinator`
 * (MySQL `GET_LOCK`)로 자동 전환하므로, **정작 이 락이 가장 필요한 "Redis 장애 상황"에서
 * Redis에 의존하지 않는다.**
 *
 * `waitTime = 0`이라 리더가 아닌 인스턴스는 즉시
 * `TooManyRequestHasBeenComeSimultaneouslyException`으로 튕겨 나오고, 스케줄러가 이를
 * "이번 턴은 다른 인스턴스가 맡았다"로 해석해 조용히 넘긴다.
 *
 * 요청 경로([AdmitSlotUseCase])에는 이 락이 없다. 요청마다 분산 락을 잡으면 걷어낸
 * `@DistributedLock(FAIR_LOCK)`을 이름만 바꿔 되살리는 셈이 된다.
 */
@UseCase
class AdmitWaitingQueueService(
    private val loadWaitingQueueSlots: LoadWaitingQueueSlots,
    private val admitSlot: AdmitSlotUseCase,
) : AdmitWaitingQueueUseCase {
    companion object {
        private const val LOCK_KEY = "'WAITING_QUEUE_ADMISSION_WORKER'"
        private const val LOCK_NO_WAIT = 0L
    }

    @DistributedLock(
        key = LOCK_KEY,
        lockType = LOCK,
        waitTime = LOCK_NO_WAIT,
        waitTimeUnit = MILLISECONDS,
    )
    override fun execute(): Int = loadWaitingQueueSlots.query().sumOf { admitSlot.execute(it) }
}
