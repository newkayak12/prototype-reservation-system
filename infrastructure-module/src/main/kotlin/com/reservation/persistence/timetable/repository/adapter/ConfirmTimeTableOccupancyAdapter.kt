package com.reservation.persistence.timetable.repository.adapter

import com.reservation.persistence.timetable.repository.jpa.TimeTableOccupancyJpaRepository
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy.ConfirmInquiry
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy.ConfirmedOccupancy
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 임시 홀드를 확정으로 전환한다.
 *
 * 대상 행을 `PESSIMISTIC_WRITE`로 잠그고 시작한다. 만료 스케줄러가 같은 행을 회수하려는 순간과
 * 겹칠 수 있는데, 잠그지 않으면 "확정된 직후에 만료 처리되는" 창이 열려 사용자가 확정 성공
 * 응답을 받고도 좌석을 잃는다.
 *
 * ## 왜 [ExpireTimeTableHoldsAdapter]와 한 클래스가 아닌가
 *
 * 처음에는 둘을 한 어댑터에 담았다. 그런데 한 `@Component`가 서로 다른 포트 둘을 구현하면,
 * 테스트에서 `@MockkBean`으로 한쪽 포트를 대체하는 순간 **그 빈이 제공하던 나머지 포트까지
 * 함께 사라진다.** 실제로 컨텍스트 로딩이 깨져서 알게 됐다. 확정은 사용자가 부르고 회수는
 * 스케줄러가 부르는, 애초에 다른 관심사이기도 하다.
 */
@Component
class ConfirmTimeTableOccupancyAdapter(
    private val timeTableOccupancyJpaRepository: TimeTableOccupancyJpaRepository,
) : ConfirmTimeTableOccupancy {
    @Transactional
    override fun confirm(inquiry: ConfirmInquiry): ConfirmedOccupancy? {
        // PENDING이 아닌 홀드는 없는 것으로 취급한다. 이미 확정된 건을 다시 확정하면 하류
        // 이벤트가 두 번 나가 예약이 중복 생성된다 — confirm을 두 번 누르는 것은 흔한 일이라
        // 반드시 걸러야 한다.
        val occupancy =
            timeTableOccupancyJpaRepository.lockActiveOccupancy(
                userId = inquiry.userId,
                restaurantId = inquiry.restaurantId,
                date = inquiry.date,
                startTime = inquiry.startTime,
            )
                .firstOrNull()
                ?.takeIf { it.isPending() }
                ?: return null

        occupancy.confirm()
        timeTableOccupancyJpaRepository.save(occupancy)

        return ConfirmedOccupancy(
            timeTableId = occupancy.timeTable.identifier!!,
            timeTableOccupancyId = occupancy.identifier!!,
        )
    }
}
