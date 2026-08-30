package com.reservation.timetable.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.timetable.TimeTable
import com.reservation.timetable.exceptions.AllTheSeatsAreAlreadyOccupiedException
import com.reservation.timetable.exceptions.AllTheThingsAreAlreadyOccupiedException
import com.reservation.timetable.port.input.OccupyTimeTableUseCase
import com.reservation.timetable.port.input.command.request.OccupyTimeTableCommand
import com.reservation.timetable.port.output.ClaimBookableTimeTable
import com.reservation.timetable.port.output.ClaimBookableTimeTable.ClaimBookableTimeTableInquiry
import com.reservation.timetable.port.output.CreateTimeTableOccupancy
import com.reservation.timetable.port.output.CreateTimeTableOccupancy.CreateTimeTableOccupancyInquiry
import com.reservation.timetable.port.output.CreateTimeTableOccupancy.TimetableOccupancyInquiry
import com.reservation.timetable.service.CreateTimeTableOccupancyDomainService
import com.reservation.timetable.snapshot.TimeTableSnapshot
import com.reservation.timetable.snapshot.TimetableOccupancySnapShot
import org.springframework.transaction.annotation.Transactional

/**
 * 좌석 확보가 끝난 요청을 DB에 **임시 홀드(PENDING)** 로 저장한다. 메시지 컨슈머가 호출한다.
 *
 * ## 여기서 다시 검사하지 않는 것들
 *
 * 대기열 통과 여부와 중복 예약, 좌석 잔량은 확인하지 않는다. 셋 다 API 경로에서 이미 원자적으로
 * 판정이 끝났고, 여기까지 메시지가 왔다는 것 자체가 "이 요청 몫으로 한 자리가 잡혀 있다"는 뜻이다.
 *
 * ## 대신 여기서만 하는 것 — 조건부 갱신으로 좌석 가져가기
 *
 * 어느 좌석 행을 줄지는 소비 시점에 [ClaimBookableTimeTable]로 **가져가면서** 고른다.
 * 발행 시점에 골라 두지 않은 이유가 이것이다 — 그때 비어 있던 행이 소비될 때까지 비어 있으리라는
 * 보장이 없다.
 *
 * 앞단에 Redis 카운터와 Kafka 키 순서 보장이 있는데도 다시 확인하는 이유는, 그 둘이 **틀릴 수
 * 있기** 때문이다. 키 순서 보장은 한 컨슈머 인스턴스 안에서만 완전하고, Redis가 죽으면 카운터를
 * 믿을 수 없다. 이 조건부 갱신은 성능 장치가 아니라 마지막 방어선이다. 정상 경로에서는 슬롯당
 * 한 번에 한 요청만 도달하므로 경합이 없고 비용도 거의 없다.
 *
 * ## 도메인 이벤트를 여기서 발행하지 않는다
 *
 * 이 시점의 점유는 아직 PENDING이다. 여기서 하류(예약 생성)로 이벤트를 보내면 사용자가 확정하지
 * 않아 만료될 홀드까지 예약으로 전파된다. 발행은 확정 시점([ConfirmTimeTableOccupancyService])으로
 * 옮겼다.
 *
 * ## 실패하면 던진다
 *
 * 좌석 되돌리기를 여기서 하지 않는다. 재시도가 남아 있는데 좌석을 반납하면 그 자리를 다른
 * 사용자가 가져가고, 뒤이은 재시도까지 성공하면서 오버부킹이 된다. 회수는 재시도를 모두
 * 소진한 시점에 한 번만 한다.
 */
@UseCase
class OccupyTimeTableService(
    private val claimBookableTimeTable: ClaimBookableTimeTable,
    private val createTimeTableOccupancy: CreateTimeTableOccupancy,
    private val createTimeTableOccupancyDomainService: CreateTimeTableOccupancyDomainService,
) : OccupyTimeTableUseCase {
    @Transactional
    override fun execute(command: OccupyTimeTableCommand): Boolean {
        val timeTable = claimBookableTimeTable(command)
        val snapshot = createTimeTableOccupancyDomainService.create(command.userId, timeTable)

        val occupancyId =
            createTimeTableOccupancy.createTimeTableOccupancy(
                snapshot.toInquiry(command.userId),
            )
                ?: throw AllTheThingsAreAlreadyOccupiedException()

        return occupancyId.isNotEmpty()
    }

    /**
     * 가져간 행에 걸린 잠금은 이 트랜잭션이 끝날 때까지 유지된다. 그래서 잡은 행에 대한 저장까지
     * 같은 트랜잭션 안에서 끝나야 하고, `@Transactional`이 `execute`에 붙어 있는 이유가 이것이다.
     */
    private fun claimBookableTimeTable(command: OccupyTimeTableCommand): TimeTable =
        claimBookableTimeTable.claim(
            ClaimBookableTimeTableInquiry(
                restaurantId = command.restaurantId,
                date = command.date,
                startTime = command.startTime,
            ),
        ) ?: throw AllTheSeatsAreAlreadyOccupiedException()

    private fun TimeTableSnapshot.toInquiry(userId: String): CreateTimeTableOccupancyInquiry =
        CreateTimeTableOccupancyInquiry(
            id = id!!,
            restaurantId = restaurantId,
            userId = userId,
            date = date,
            day = day,
            startTime = startTime,
            endTime = endTime,
            tableNumber = tableNumber,
            tableSize = tableSize,
            tableStatus = tableStatus,
            timetableOccupancy = timetableOccupancy!!.toInquiry(),
        )

    private fun TimetableOccupancySnapShot.toInquiry(): TimetableOccupancyInquiry =
        TimetableOccupancyInquiry(
            id = id,
            timeTableId = timeTableId,
            userId = userId,
            occupiedStatus = occupiedStatus,
            occupiedDatetime = occupiedDatetime,
            unoccupiedDatetime = unoccupiedDatetime,
        )
}
