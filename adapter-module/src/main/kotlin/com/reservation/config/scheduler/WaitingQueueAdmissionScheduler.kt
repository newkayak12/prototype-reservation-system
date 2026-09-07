package com.reservation.config.scheduler

import com.reservation.queue.port.input.AdmitWaitingQueueUseCase
import com.reservation.timetable.exceptions.TooManyRequestHasBeenComeSimultaneouslyException
import com.reservation.utilities.logger.loggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 대기열 리컨실 워커.
 *
 * **큐를 미는 주된 주체가 아니다.** 승격은 사용자 요청(진입·폴링)이 직접 하고, 이 워커는
 * 그 경로가 닿지 못하는 자리만 뒤늦게 치운다 — permit lease가 조용히 만료된 경우, 홀드
 * 만료로 좌석이 돌아온 경우, 반납 직후 인스턴스가 죽어 승격이 유실된 경우. 자세한 근거는
 * `AdmitWaitingQueueService`에 적어 두었다.
 *
 * 그래서 주기는 응답성이 아니라 **유실 복구를 얼마나 늦게 알아채도 되는가**로 정한다.
 * 짧게 잡을수록 살아있는 슬롯 전수 조회만 잦아진다 — 슬롯이 200개면 한 턴에 Redis 왕복이
 * 200번이고, 이 비용은 대기자가 0명이어도 그대로 든다.
 *
 * `AdmitWaitingQueueUseCase.execute()`에 걸린 분산 락이 "한 번에 한 인스턴스"를 보장한다.
 * 락을 잡지 못한 인스턴스는 [TooManyRequestHasBeenComeSimultaneouslyException]으로 즉시
 * 튕겨 나오는데, 이는 오류가 아니라 "이번 턴은 다른 인스턴스가 맡았다"는 뜻이므로
 * 조용히 넘긴다. (여기서 삼키지 않으면 리더가 아닌 인스턴스가 주기마다 에러 로그를 쏟는다.)
 */
@Component
class WaitingQueueAdmissionScheduler(
    private val admitWaitingQueueUseCase: AdmitWaitingQueueUseCase,
) {
    private val log = loggerFactory<WaitingQueueAdmissionScheduler>()

    @Scheduled(fixedDelayString = "\${reservation.queue.reconcile-interval-millis:5000}")
    fun admit() {
        val admitted =
            try {
                admitWaitingQueueUseCase.execute()
            } catch (exception: TooManyRequestHasBeenComeSimultaneouslyException) {
                log.debug("another instance is running the admission worker: {}", exception.message)
                return
            }

        // 여기서 잡히는 수가 꾸준히 크다면 요청 경로의 승격이 제 몫을 못 하고 있다는 신호다.
        // 정상 상태에서는 대부분의 턴이 0이어야 한다.
        if (admitted > 0) log.info("waiting queue reconciled {} ticket(s)", admitted)
    }
}
