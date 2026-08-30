package com.reservation.timetable.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.queue.exceptions.QueueNotAdmittedException
import com.reservation.queue.port.output.IsUserAdmitted
import com.reservation.queue.port.output.IsUserAdmitted.UserAdmissionInquiry
import com.reservation.queue.port.output.ReleaseAdmission
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.timetable.exceptions.AllTheSeatsAreAlreadyOccupiedException
import com.reservation.timetable.exceptions.AlreadyBookedThisSlotException
import com.reservation.timetable.exceptions.TimeTableOccupancyRequestNotPublishedException
import com.reservation.timetable.port.input.CreateTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import com.reservation.timetable.port.output.AcquireTimeTableSeat
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.ACQUIRED
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.DUPLICATED
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.SOLD_OUT
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatInquiry
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.port.output.LoadBookableTimeTables.LoadBookableTimeTablesInquiry
import com.reservation.timetable.port.output.PublishTimeTableOccupancyRequest
import com.reservation.timetable.port.output.PublishTimeTableOccupancyRequest.TimeTableOccupancyRequest
import com.reservation.timetable.port.output.ReleaseTimeTableSeat
import com.reservation.timetable.port.output.ReleaseTimeTableSeat.SeatReleaseInquiry

/**
 * 좌석 점유(예약) 요청 접수.
 *
 * ## 이 메소드에서 걷어낸 것들
 *
 * 원래는 한 메소드에 네 겹이 쌓여 있었다 — `@RateLimiter`, `@DistributedLock(FAIR_LOCK)`,
 * 대기열 게이트, 좌석 세마포어. 그 위에 `@Transactional` 안에서 DB 저장까지 했다.
 *
 * - `@DistributedLock(FAIR_LOCK, waitTime = 2분)`은 슬롯 단위로 요청을 **직렬화**해 정합성을
 *   지켰다. 정확하지만 대기가 곧 응답시간이라 부하가 오를수록 선형으로 느려지고, 2분 안에
 *   차례가 오지 않으면 실패한다. 이번 재설계에서 가장 큰 병목이었다.
 * - 좌석 세마포어(`AcquireTimeTableSemaphore`)는 `RSemaphore.trySetPermits(capacity, ttl)`을
 *   쓰는데, 이 호출은 키가 이미 있으면 아무 일도 하지 않아 TTL이 "키 생성 시각"에 고정된다.
 *   permit이 키 만료 때 한꺼번에 돌아오므로 capacity가 "동시 허용치"가 아니라 "TTL 주기마다
 *   리셋되는 예산"으로 동작했다 — 즉 **경계에서 좌석을 다시 팔 수 있었다.**
 *
 * 둘의 자리를 [AcquireTimeTableSeat] 하나가 대신한다. Redis가 Lua 스크립트를 단일 스레드로
 * 끝까지 실행한다는 성질에 기대어, 중복 차단과 좌석 차감을 한 번의 원자 연산으로 끝낸다.
 * 아무도 기다리지 않고, 진 쪽은 즉시 품절 응답을 받는다.
 *
 * ## DB 저장도 여기서 하지 않는다
 *
 * 좌석 경합의 승패는 Redis에서 이미 갈렸는데, 이전 구현은 그 뒤에 timetable 조회와 두 번의
 * INSERT를 같은 스레드에서 마치고 나서야 응답했다. 이제는 [PublishTimeTableOccupancyRequest]로
 * 넘기는 순간 API의 일이 끝나고, 저장은 [OccupyTimeTableService]가 맡는다. 슬롯을 파티션 키로
 * 쓰기 때문에 같은 슬롯의 요청은 컨슈머에서 한 줄로 처리된다.
 *
 * 그래서 이 메소드에는 `@Transactional`이 없다. 쓰기가 없으니 트랜잭션을 열 이유가 없다.
 *
 * `@RateLimiter`도 걷어냈다 — 다만 이건 설계 판단이 아니라 **측정 조건을 맞추기 위한 것**이다.
 * 슬롯당 1,000 req/s 버킷은 VU 1,500~2,000 버스트를 아키텍처가 아니라 레이트리밋으로 잘라내서,
 * 켜 둔 채로는 before/after가 서로 다른 조건에서 측정된다(before 쪽에서도 같은 이유로 제거했다).
 * 외곽 방어 자체는 필요하므로, 측정이 끝나면 게이트웨이 층에 두는 편이 맞다.
 */
@UseCase
class CreateTimeTableOccupancyService(
    private val loadBookableTimeTables: LoadBookableTimeTables,
    private val isUserAdmitted: IsUserAdmitted,
    private val releaseAdmission: ReleaseAdmission,
    private val acquireTimeTableSeat: AcquireTimeTableSeat,
    private val releaseTimeTableSeat: ReleaseTimeTableSeat,
    private val publishTimeTableOccupancyRequest: PublishTimeTableOccupancyRequest,
) : CreateTimeTableOccupancyUseCase {
    // 측정 기간 동안만 꺼 둔다. 아래가 걷어내기 전 원형이다.
    //
    //  @RateLimiter(
    //      key = "'TIME_TABLE:' + #command.restaurantId + ':' + #command.date + ':' +
    //             #command.startTime",
    //      type = RateLimitType.WHOLE,
    //      rate = 1000L,               // 슬롯당 1,000 req/s
    //      maximumWaitTime = 3L,
    //      rateIntervalTime = 1L,
    //      bucketLiveTime = 1L,
    //  )
    //
    // 다시 켜는 것은 그대로 되돌리는 일이 아니다. 이 버킷은 VU 1,500~2,000 버스트를
    // 아키텍처가 아니라 레이트리밋으로 잘라내서, 켜 둔 채로는 before/after가 서로 다른
    // 조건에서 측정된다(before 쪽에서도 같은 이유로 뗐다). 외곽 방어 자체는 필요하므로,
    // 측정이 끝나면 여기가 아니라 게이트웨이 층에 두는 편이 맞다.
    override fun execute(command: CreateTimeTableOccupancyCommand): Boolean {
        verifyAdmitted(command)

        // 품절과 중복은 **종착 거절**이다 — 같은 슬롯에 다시 시도해도 결과가 달라지지 않는다.
        // 그러니 자리를 붙들고 나갈 이유가 없고, 붙들고 나가면 뒤에 줄 선 사람이 그 자리를
        // 영영 못 받는다. 실측에서 이게 그대로 드러났다: 정원 100짜리 대기열이 VU 300이든
        // 3,000이든 **130명만 통과시키고 멈췄다**(정원 100 + 성공한 30명이 돌려준 몫).
        // 거절당한 70명이 자리를 쥔 채 끝나 회전이 죽은 것이다.
        try {
            acquireSeat(command, countBookableSeats(command))
        } catch (exception: AllTheSeatsAreAlreadyOccupiedException) {
            releaseAdmission.release(slotOf(command), command.userId)
            throw exception
        } catch (exception: AlreadyBookedThisSlotException) {
            releaseAdmission.release(slotOf(command), command.userId)
            throw exception
        }

        // 여기부터는 자리를 잡은 상태다. 뒷단으로 넘기지 못하면 반드시 되돌린다 —
        // 안 그러면 아무도 쓰지 않는 자리가 카운터 TTL이 끝날 때까지 묶인다.
        //
        // 다만 입장 자격은 돌려주지 않는다. 이건 좌석이 없어서가 아니라 인프라가 흔들려서
        // 생긴 실패라 재시도할 여지가 있고, 여기서 자격을 회수하면 사용자가 자기 잘못도
        // 아닌 이유로 대기열 맨 뒤로 밀린다. 방치되더라도 permit의 lease가 만료되며 회수된다.
        if (!publishRequest(command)) {
            releaseSeat(command)
            throw TimeTableOccupancyRequestNotPublishedException()
        }

        // 예약이 접수됐으니 입장 자리를 대기열에 돌려준다.
        releaseAdmission.release(slotOf(command), command.userId)

        return true
    }

    /**
     * 대기열 강제 게이트.
     *
     * 대기열을 우회해 booking을 직접 호출하는 경로를 원천 차단한다 — 입장 허용되지 않은
     * 사용자면 좌석 조회/차감을 건드리기 전에 즉시 거절한다.
     *
     * 판단 기준은 **인증에서 얻은 `userId`뿐**이고, 요청 본문의 ticketId는 쓰지 않는다.
     * 클라이언트가 보낸 티켓을 믿으면 티켓이 유출되거나 추측되는 순간 게이트가 무력화된다.
     * 서버가 `TICKET_OF:{key}:{userId}`로 티켓을 되찾아오는 일은 [IsUserAdmitted]가 맡는다.
     */
    private fun slotOf(command: CreateTimeTableOccupancyCommand) =
        WaitingQueueSlot(
            restaurantId = command.restaurantId,
            date = command.date,
            startTime = command.startTime,
        )

    private fun verifyAdmitted(command: CreateTimeTableOccupancyCommand) {
        val admitted =
            isUserAdmitted.query(
                UserAdmissionInquiry(slot = slotOf(command), userId = command.userId),
            )

        if (!admitted) throw QueueNotAdmittedException()
    }

    /**
     * 좌석 카운터에 심을 초기값을 구한다.
     *
     * 이 조회 결과에서 **어느 행을 쓸지는 고르지 않는다.** 개수만 쓴다. 특정 행 선택은
     * 컨슈머가 소비 시점에 다시 한다 — 지금 비어 있는 행이 저장 시점에도 비어 있다는
     * 보장이 없기 때문이다.
     */
    private fun countBookableSeats(command: CreateTimeTableOccupancyCommand): Int {
        val inquiry =
            LoadBookableTimeTablesInquiry(
                restaurantId = command.restaurantId,
                date = command.date,
                startTime = command.startTime,
            )
        val count = loadBookableTimeTables.query(inquiry).size
        if (count == 0) throw AllTheSeatsAreAlreadyOccupiedException()

        return count
    }

    private fun acquireSeat(
        command: CreateTimeTableOccupancyCommand,
        availableSeats: Int,
    ) {
        val acquisition =
            acquireTimeTableSeat.acquire(
                SeatInquiry(
                    restaurantId = command.restaurantId,
                    date = command.date,
                    startTime = command.startTime,
                    userId = command.userId,
                    availableSeats = availableSeats,
                ),
            )

        when (acquisition) {
            ACQUIRED -> Unit
            DUPLICATED -> throw AlreadyBookedThisSlotException()
            SOLD_OUT -> throw AllTheSeatsAreAlreadyOccupiedException()
        }
    }

    private fun publishRequest(command: CreateTimeTableOccupancyCommand): Boolean =
        publishTimeTableOccupancyRequest.publish(
            TimeTableOccupancyRequest(
                restaurantId = command.restaurantId,
                date = command.date,
                startTime = command.startTime,
                userId = command.userId,
            ),
        )

    private fun releaseSeat(command: CreateTimeTableOccupancyCommand) {
        releaseTimeTableSeat.release(
            SeatReleaseInquiry(
                restaurantId = command.restaurantId,
                date = command.date,
                startTime = command.startTime,
                userId = command.userId,
            ),
        )
    }
}
