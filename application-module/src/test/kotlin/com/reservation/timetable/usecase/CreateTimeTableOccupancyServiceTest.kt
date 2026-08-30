package com.reservation.timetable.usecase

import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.queue.exceptions.QueueNotAdmittedException
import com.reservation.queue.port.output.IsUserAdmitted
import com.reservation.queue.port.output.IsUserAdmitted.UserAdmissionInquiry
import com.reservation.queue.port.output.ReleaseAdmission
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.timetable.TimeTable
import com.reservation.timetable.exceptions.AllTheSeatsAreAlreadyOccupiedException
import com.reservation.timetable.exceptions.AlreadyBookedThisSlotException
import com.reservation.timetable.exceptions.TimeTableOccupancyRequestNotPublishedException
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import com.reservation.timetable.port.output.AcquireTimeTableSeat
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.ACQUIRED
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.DUPLICATED
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatAcquisition.SOLD_OUT
import com.reservation.timetable.port.output.AcquireTimeTableSeat.SeatInquiry
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.port.output.PublishTimeTableOccupancyRequest
import com.reservation.timetable.port.output.PublishTimeTableOccupancyRequest.TimeTableOccupancyRequest
import com.reservation.timetable.port.output.ReleaseTimeTableSeat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 사용자 요청 경로만 검증한다.
 *
 * 저장은 더 이상 여기서 하지 않는다 — 좌석을 확보하고 뒷단으로 넘기는 데까지가 이 서비스의
 * 일이고, 실제 INSERT는 [OccupyTimeTableServiceTest]가 맡는다. 그래서 이 파일에는 도메인
 * 스냅샷도, `CreateTimeTableOccupancy`도 등장하지 않는다.
 */
@ExtendWith(MockKExtension::class)
class CreateTimeTableOccupancyServiceTest {
    @MockK
    private lateinit var acquireTimeTableSeat: AcquireTimeTableSeat

    @MockK
    private lateinit var releaseTimeTableSeat: ReleaseTimeTableSeat

    @MockK
    private lateinit var loadBookableTimeTables: LoadBookableTimeTables

    @MockK
    private lateinit var publishTimeTableOccupancyRequest: PublishTimeTableOccupancyRequest

    @MockK
    private lateinit var isUserAdmitted: IsUserAdmitted

    @MockK
    private lateinit var releaseAdmission: ReleaseAdmission

    @SpyK
    @InjectMockKs
    private lateinit var createTimeTableOccupancyService: CreateTimeTableOccupancyService

    private lateinit var pureMonkey: FixtureMonkey

    @BeforeEach
    fun init() {
        pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        clearMocks(
            acquireTimeTableSeat,
            releaseTimeTableSeat,
            loadBookableTimeTables,
            publishTimeTableOccupancyRequest,
            isUserAdmitted,
            releaseAdmission,
        )

        // 대기열 강제 게이트는 기본적으로 통과시켜 두고, 게이트 자체를 검증하는
        // 시나리오에서만 false로 덮어쓴다.
        every { isUserAdmitted.query(any()) } returns true
        every { releaseAdmission.release(any(), any()) } just Runs
    }

    // Scenario 1: 예약 가능한 테이블이 없는 경우
    @DisplayName("요청한 시간대에 예약 가능한 테이블이 없을 때")
    @Nested
    inner class `Request income but all the seats are already occupied` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("AllTheSeatsAreAlreadyOccupiedException이 발생한다")
            @Test
            fun `throw AllTheSeatsAreAlreadyOccupiedException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every { loadBookableTimeTables.query(any()) } returns emptyList()

                shouldThrow<AllTheSeatsAreAlreadyOccupiedException> {
                    createTimeTableOccupancyService.execute(command)
                }

                verify(exactly = 0) {
                    acquireTimeTableSeat.acquire(any())
                    releaseTimeTableSeat.release(any())
                    publishTimeTableOccupancyRequest.publish(any())
                }
                verify(exactly = 1) { loadBookableTimeTables.query(any()) }

                // 종착 거절이므로 입장 자리는 돌려준다. 붙들고 나가면 뒤에 줄 선 사람이
                // 그 자리를 영영 못 받는다.
                verify(exactly = 1) { releaseAdmission.release(any(), command.userId) }
            }
        }
    }

    // Scenario 2: 좌석 품절 (Lua 원자 차감이 거절)
    //
    // 이전에는 세마포어 permit 실패라 AllTheThingsAreAlreadyOccupiedException이었다.
    // 좌석 카운터로 바뀌면서 "자리가 없다"는 뜻이 명확해져 Seats 쪽 예외로 옮겼다.
    @DisplayName("사용 가능한 테이블은 있지만 좌석 차감에서 밀렸을 때")
    @Nested
    inner class `Request income but seat is sold out` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("AllTheSeatsAreAlreadyOccupiedException이 발생한다")
            @Test
            fun `throw AllTheSeatsAreAlreadyOccupiedException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every { loadBookableTimeTables.query(any()) } returns pureMonkey.giveMe(4)
                every { acquireTimeTableSeat.acquire(any()) } returns SOLD_OUT

                shouldThrow<AllTheSeatsAreAlreadyOccupiedException> {
                    createTimeTableOccupancyService.execute(command)
                }

                // 자리를 잡지 못했으므로 되돌릴 것도 없다. 여기서 release를 부르면
                // 남의 자리를 하나 만들어 주는 꼴이 된다.
                verify(exactly = 0) {
                    releaseTimeTableSeat.release(any())
                    publishTimeTableOccupancyRequest.publish(any())
                }
                verify(exactly = 1) {
                    acquireTimeTableSeat.acquire(any())
                    loadBookableTimeTables.query(any())
                }

                // 좌석은 못 잡았어도 입장 자리는 쥐고 있었다. 품절은 재시도해도 결과가 같은
                // 종착 거절이므로 여기서 돌려주지 않으면 대기열 회전이 그대로 멈춘다.
                verify(exactly = 1) { releaseAdmission.release(any(), command.userId) }
            }
        }
    }

    // Scenario 3: 슬롯당 1인 1예약 위반
    @DisplayName("같은 사용자가 이미 이 슬롯을 잡아 두었을 때")
    @Nested
    inner class `Request income but user already booked this slot` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("AlreadyBookedThisSlotException이 발생한다")
            @Test
            fun `throw AlreadyBookedThisSlotException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every { loadBookableTimeTables.query(any()) } returns pureMonkey.giveMe(4)
                every { acquireTimeTableSeat.acquire(any()) } returns DUPLICATED

                shouldThrow<AlreadyBookedThisSlotException> {
                    createTimeTableOccupancyService.execute(command)
                }

                // 중복은 좌석을 깎지 않고 거절된다. 여기서 release를 부르면 앞선 요청이
                // 정상적으로 쥐고 있는 중복 마커를 지워 버린다.
                verify(exactly = 0) {
                    releaseTimeTableSeat.release(any())
                    publishTimeTableOccupancyRequest.publish(any())
                }

                // 좌석(release)과 달리 입장 자리(releaseAdmission)는 돌려준다. 이미 이 슬롯을
                // 잡아 둔 사용자는 다시 시도해도 같은 거절을 받으므로 자격이 무의미하다.
                verify(exactly = 1) { releaseAdmission.release(any(), command.userId) }
            }
        }
    }

    // Scenario 4: 발행 실패
    // 좌석은 잡혔는데 뒷단으로 넘기지 못한 경우다. 되돌리지 않으면 그 한 자리는 아무도
    // 쓰지 못한 채 카운터 TTL이 끝날 때까지 묶인다.
    @DisplayName("좌석은 확보했지만 요청 발행에 실패했을 때")
    @Nested
    inner class `Request income but publishing is failed` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("좌석을 되돌리고 예외가 발생한다")
            @Test
            fun `release the seat and throw`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every { loadBookableTimeTables.query(any()) } returns pureMonkey.giveMe(4)
                every { acquireTimeTableSeat.acquire(any()) } returns ACQUIRED
                every { publishTimeTableOccupancyRequest.publish(any()) } returns false
                every { releaseTimeTableSeat.release(any()) } just Runs

                shouldThrow<TimeTableOccupancyRequestNotPublishedException> {
                    createTimeTableOccupancyService.execute(command)
                }

                verify(exactly = 1) {
                    acquireTimeTableSeat.acquire(any())
                    publishTimeTableOccupancyRequest.publish(any())
                    releaseTimeTableSeat.release(any())
                }

                // 입장 자격은 뺏지 않는다. 재시도할 여지가 있는 실패에서 자격까지 회수하면
                // 사용자가 대기열 맨 뒤로 밀린다.
                verify(exactly = 0) { releaseAdmission.release(any(), any()) }
            }
        }
    }

    // Scenario 5: 정상 접수
    @DisplayName("좌석을 확보하고 요청 발행에도 성공했을 때")
    @Nested
    inner class `Request income and booking accepted` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("좌석을 되돌리지 않고 true를 반환한다")
            @Test
            fun `accept and return true`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every { loadBookableTimeTables.query(any()) } returns pureMonkey.giveMe(4)
                every { acquireTimeTableSeat.acquire(any()) } returns ACQUIRED
                every { publishTimeTableOccupancyRequest.publish(any()) } returns true

                createTimeTableOccupancyService.execute(command) shouldBe true

                verify(exactly = 1) {
                    acquireTimeTableSeat.acquire(any())
                    publishTimeTableOccupancyRequest.publish(any())
                }
                verify(exactly = 0) { releaseTimeTableSeat.release(any()) }
            }

            // 명시적으로 돌려주지 않으면 예약을 마친 사용자의 입장 자리가 lease 만료까지
            // 묶여, 대기 중인 다음 사람이 그만큼 더 기다린다.
            @DisplayName("입장 자리를 대기열에 돌려준다")
            @Test
            fun `give the admission slot back`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val slot = slot<WaitingQueueSlot>()

                every { loadBookableTimeTables.query(any()) } returns pureMonkey.giveMe(4)
                every { acquireTimeTableSeat.acquire(any()) } returns ACQUIRED
                every { publishTimeTableOccupancyRequest.publish(any()) } returns true
                every { releaseAdmission.release(capture(slot), any()) } just Runs

                createTimeTableOccupancyService.execute(command)

                verify(exactly = 1) { releaseAdmission.release(any(), command.userId) }
                slot.captured.restaurantId shouldBe command.restaurantId
                slot.captured.date shouldBe command.date
                slot.captured.startTime shouldBe command.startTime
            }

            @DisplayName("조회된 테이블 개수를 좌석 카운터 초기값으로 넘긴다")
            @Test
            fun `seed the seat counter with the bookable table count`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val bookable = pureMonkey.giveMe<TimeTable>(7)
                val seatInquiry = slot<SeatInquiry>()

                every { loadBookableTimeTables.query(any()) } returns bookable
                every { acquireTimeTableSeat.acquire(capture(seatInquiry)) } returns ACQUIRED
                every { publishTimeTableOccupancyRequest.publish(any()) } returns true

                createTimeTableOccupancyService.execute(command)

                // 초기값이 실제 좌석 수와 어긋나면 오버부킹이나 언더부킹이 그대로 나온다.
                seatInquiry.captured.availableSeats shouldBe bookable.size
                seatInquiry.captured.userId shouldBe command.userId
                seatInquiry.captured.restaurantId shouldBe command.restaurantId
            }

            @DisplayName("발행하는 요청에 명령의 슬롯과 사용자를 그대로 담는다")
            @Test
            fun `publish the request with the very slot of the command`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val published = slot<TimeTableOccupancyRequest>()

                every { loadBookableTimeTables.query(any()) } returns pureMonkey.giveMe(4)
                every { acquireTimeTableSeat.acquire(any()) } returns ACQUIRED
                every {
                    publishTimeTableOccupancyRequest.publish(capture(published))
                } returns true

                createTimeTableOccupancyService.execute(command)

                published.captured.restaurantId shouldBe command.restaurantId
                published.captured.date shouldBe command.date
                published.captured.startTime shouldBe command.startTime
                published.captured.userId shouldBe command.userId
            }
        }
    }

    // Scenario 6: 대기열 강제 게이트
    @DisplayName("입장 허용되지 않은 사용자가 예약을 요청할 때")
    @Nested
    inner class `Request income with user which is not admitted` {
        @DisplayName("예약 생성을 요청하면")
        @Nested
        inner class `When request booking` {
            @DisplayName("QueueNotAdmittedException이 발생한다")
            @Test
            fun `throw QueueNotAdmittedException`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()

                every { isUserAdmitted.query(any()) } returns false

                shouldThrow<QueueNotAdmittedException> {
                    createTimeTableOccupancyService.execute(command)
                }

                verify(exactly = 1) { isUserAdmitted.query(any()) }
                verify(exactly = 0) {
                    loadBookableTimeTables.query(any())
                    acquireTimeTableSeat.acquire(any())
                    publishTimeTableOccupancyRequest.publish(any())
                }
            }
        }
    }

    // Scenario 7: 게이트의 판단 근거
    @DisplayName("예약 요청이 들어왔을 때")
    @Nested
    inner class `Request income to booking gate` {
        @DisplayName("대기열 게이트가 동작하면")
        @Nested
        inner class `When gate runs` {
            // ticketId가 nonce로 바뀌면서 서버가 같은 값을 다시 계산할 수 없게 됐다.
            // 그래서 게이트는 티켓을 계산하는 대신 아예 묻지 않는다 — userId만 넘기고,
            // 티켓을 되찾아오는 일은 어댑터가 TICKET_OF 조회로 처리한다.
            @DisplayName("클라이언트 ticketId를 받지 않고 userId와 슬롯만으로 조회한다")
            @Test
            fun `ask by user id not by client supplied ticket`() {
                val command = pureMonkey.giveMeOne<CreateTimeTableOccupancyCommand>()
                val inquirySlot = slot<UserAdmissionInquiry>()

                every { isUserAdmitted.query(capture(inquirySlot)) } returns false

                shouldThrow<QueueNotAdmittedException> {
                    createTimeTableOccupancyService.execute(command)
                }

                inquirySlot.captured.userId shouldBe command.userId
                inquirySlot.captured.slot.restaurantId shouldBe command.restaurantId
                inquirySlot.captured.slot.date shouldBe command.date
                inquirySlot.captured.slot.startTime shouldBe command.startTime
            }
        }
    }
}
