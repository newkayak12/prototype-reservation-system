package com.reservation.config.scheduler

import com.reservation.timetable.port.input.ExpireTimeTableHoldsUseCase
import com.reservation.utilities.logger.loggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 확정되지 않은 채 시간이 지난 좌석 홀드를 회수하는 워커.
 *
 * 예약은 좌석을 **임시로** 잡을 뿐이라, 사용자가 확정하지 않으면 그 자리는 아무도 쓰지 못한 채
 * 묶인다. 좌석이 30개뿐인 슬롯에서는 몇 명만 그래도 매장이 통째로 팔 수 없는 상태가 된다.
 *
 * ## 왜 batch-module이 아니라 여기인가
 *
 * 계획 초안은 `batch-module`이었다. 그런데 회수는 DB만으로 끝나지 않고 Redis 좌석 카운터와
 * 중복 마커까지 되돌려야 하는데, 그 어댑터들이 이 애플리케이션 컨텍스트에 있다. 별도 앱에서
 * 돌리려면 Redis 배선을 통째로 한 벌 더 만들어야 하고, 그러면 "좌석을 되돌리는 방법"이 두 군데로
 * 갈라진다. 같은 주기 워커인 [WaitingQueueAdmissionScheduler] 옆에 두는 편이 일관적이다.
 *
 * ## 여러 인스턴스에서 동시에 돌면
 *
 * 회수 대상 조회가 `PESSIMISTIC_WRITE`로 행을 잠그므로, 두 인스턴스가 같은 주기에 돌아도 같은
 * 홀드를 두 번 처리하지 않는다. 그래서 입장 워커와 달리 별도의 분산 락을 두지 않았다.
 */
@Component
class TimeTableHoldExpiryScheduler(
    private val expireTimeTableHoldsUseCase: ExpireTimeTableHoldsUseCase,
) {
    private val log = loggerFactory<TimeTableHoldExpiryScheduler>()

    @Scheduled(fixedDelayString = "\${reservation.timetable.hold-expiry-interval-millis:10000}")
    fun expire() {
        val expired = expireTimeTableHoldsUseCase.execute()

        if (expired > 0) log.info("expired {} unconfirmed seat hold(s)", expired)
    }
}
