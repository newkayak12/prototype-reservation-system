package com.reservation.persistence.timetable.repository.adapter

import com.reservation.persistence.timetable.entity.TimeTableEntity
import com.reservation.persistence.timetable.repository.jpa.TimeTableJpaRepository
import com.reservation.timetable.TimeTable
import com.reservation.timetable.port.output.ClaimBookableTimeTable
import com.reservation.timetable.port.output.ClaimBookableTimeTable.ClaimBookableTimeTableInquiry
import org.springframework.stereotype.Component

/**
 * 예약 가능한 좌석 한 행을 **조건부 UPDATE로 가져간다**.
 *
 * ## 두 단계인 이유
 *
 * 어느 행을 노릴지는 알아야 조건부 갱신을 걸 수 있는데, JPQL의 UPDATE에는 `LIMIT`도
 * 서브쿼리로 자기 테이블을 고르는 방법도 없다. 그래서 후보를 먼저 읽고(잠그지 않는다),
 * 그중 하나를 갱신으로 가져간다. 후보 목록이 낡아도 상관없다 — 판정은 전적으로
 * `claimTimeTable`의 `AND tableStatus = 'EMPTY'`가 하고, 낡은 후보는 0행으로 떨어질 뿐이다.
 *
 * ## 후보를 섞는 이유
 *
 * 모두가 같은 순서로 읽으면 전원이 첫 번째 행에 달라붙는다. 한 명이 이기고 나머지는 그 행의
 * 잠금이 풀릴 때까지 기다렸다가 0행을 받고 두 번째 행으로 몰려간다 — 좌석 수만큼 줄서기가
 * 반복된다. 시작 지점을 흩어 두면 서로 다른 행을 노리므로 대부분 첫 시도에 끝난다.
 *
 * ## 왜 [LoadTimeTableAdapter]와 한 클래스가 아닌가
 *
 * 가져가는 쪽과 그냥 읽는 쪽은 같은 JPQL을 쓰지만, 한 `@Component`가 두 포트를 구현하면
 * 테스트에서 `@MockkBean`으로 한쪽을 대체하는 순간 **그 빈이 제공하던 나머지 포트까지 함께
 * 사라진다.** 실제로 그것 때문에 컨텍스트 로딩이 깨진 적이 있어 처음부터 나눠 둔다.
 */
@Component
class ClaimTimeTableAdapter(
    private val timeTableJpaRepository: TimeTableJpaRepository,
) : ClaimBookableTimeTable {
    override fun claim(inquiry: ClaimBookableTimeTableInquiry): TimeTable? =
        timeTableJpaRepository.findBookableTimeTable(
            restaurantId = inquiry.restaurantId,
            date = inquiry.date,
            startTime = inquiry.startTime,
        )
            .shuffled()
            .firstOrNull { timeTableJpaRepository.claimTimeTable(it.identifier) == 1 }
            ?.toDomainEntity()

    /**
     * 가져가기 직전 모습 그대로 돌려준다. `tableStatus`는 후보 조회가 `EMPTY`로 걸러 온 값이고,
     * `OCCUPIED`로의 전이는 도메인이 자기 층에서 다시 표현한다.
     */
    private fun TimeTableEntity.toDomainEntity() =
        TimeTable(
            id = identifier,
            restaurantId = restaurantId,
            date = date,
            day = day,
            startTime = startTime,
            endTime = endTime,
            tableNumber = tableNumber,
            tableSize = tableSize,
            tableStatus = tableStatus,
            timeTableConfirmStatus = timeTableConfirmStatus,
            timetableOccupancy = null,
        )
}
