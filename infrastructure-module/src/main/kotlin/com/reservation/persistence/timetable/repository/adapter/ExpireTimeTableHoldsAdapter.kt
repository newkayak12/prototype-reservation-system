package com.reservation.persistence.timetable.repository.adapter

import com.reservation.enumeration.TableStatus.EMPTY
import com.reservation.persistence.timetable.entity.TimeTableOccupancyEntity
import com.reservation.persistence.timetable.repository.jpa.TimeTableOccupancyJpaRepository
import com.reservation.timetable.port.output.ExpireTimeTableHolds
import com.reservation.timetable.port.output.ExpireTimeTableHolds.ExpireInquiry
import com.reservation.timetable.port.output.ExpireTimeTableHolds.ExpiredHold
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 확정되지 않은 채 시간이 지난 홀드를 회수한다.
 *
 * 회수 대상을 `PESSIMISTIC_WRITE`로 잠그고 가져오므로, 스케줄러가 여러 인스턴스에서 동시에
 * 돌아도 같은 홀드를 두 번 처리하지 않는다. 사용자의 확정 요청과 겹치는 경우도 같은 잠금이 막는다.
 */
@Component
class ExpireTimeTableHoldsAdapter(
    private val timeTableOccupancyJpaRepository: TimeTableOccupancyJpaRepository,
) : ExpireTimeTableHolds {
    @Transactional
    override fun expire(inquiry: ExpireInquiry): List<ExpiredHold> {
        val expired =
            timeTableOccupancyJpaRepository.lockExpiredHolds(
                threshold = inquiry.heldBefore,
                pageable = PageRequest.of(0, inquiry.limit),
            )

        if (expired.isEmpty()) return emptyList()

        expired.forEach { it.releaseWithTable() }
        timeTableOccupancyJpaRepository.saveAll(expired)

        return expired.map { it.toExpiredHold() }
    }

    /**
     * 점유를 풀면서 좌석 행도 함께 `EMPTY`로 되돌린다.
     *
     * 둘 중 하나만 하면 안 된다. 점유만 풀고 `table_status`를 `OCCUPIED`로 두면 예약 가능 조회가
     * 그 행을 계속 건너뛰어, DB상으로는 비어 있는데 아무도 살 수 없는 좌석이 된다.
     */
    private fun TimeTableOccupancyEntity.releaseWithTable() {
        release()
        timeTable.modifyTableStatus(EMPTY)
    }

    private fun TimeTableOccupancyEntity.toExpiredHold() =
        ExpiredHold(
            restaurantId = timeTable.restaurantId,
            date = timeTable.date,
            startTime = timeTable.startTime,
            userId = userId,
        )
}
