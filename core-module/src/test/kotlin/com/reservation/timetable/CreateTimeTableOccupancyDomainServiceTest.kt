package com.reservation.timetable

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.enumeration.OccupyStatus
import com.reservation.enumeration.TableStatus
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.timetable.policy.exceptions.InvalidTimeTableIdException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableStatusException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableUserIdException
import com.reservation.timetable.service.CreateTimeTableOccupancyDomainService
import com.reservation.timetable.snapshot.TimeTableSnapshot
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import net.jqwik.api.Arbitraries

class CreateTimeTableOccupancyDomainServiceTest : BehaviorSpec(
    {

        val domainService = CreateTimeTableOccupancyDomainService()

        // Scenario 1: UserId 검증 실패 케이스들
        Given("빈 문자열 userId가 주어졌을 때") {
            val emptyUserId = ""
            val timetable = giveMePerfectCase(UuidGenerator.generate())

            When("예약 점유 생성을 요청하면") {

                Then("InvalidTimetableUserIdException(\"User ID is empty.\")가 발생한다") {
                    val exception =
                        shouldThrow<InvalidTimeTableUserIdException> {
                            domainService.create(emptyUserId, timetable)
                        }

                    exception.message shouldBe "User ID is empty."
                }
            }
        }

        Given("UUID 형식이 아닌 userId가 주어졌을 때") {
            val emptyUserId = Arbitraries.strings().ofLength(128).sample()
            val timetable = giveMePerfectCase(UuidGenerator.generate())

            When("예약 점유 생성을 요청하면") {
                Then("InvalidTimetableUserIdException(\"User ID has invalid format.\")가 발생한다") {
                    val exception =
                        shouldThrow<InvalidTimeTableUserIdException> {
                            domainService.create(emptyUserId, timetable)
                        }

                    exception.message shouldBe "User ID has invalid format."
                }
            }
        }

        // Scenario 2: TimeTableId 검증 실패 케이스들
        Given("null timeTableId를 가진 TimeTable이 주어졌을 때") {
            val userId = UuidGenerator.generate()
            val timeTableId = null

            When("예약 점유 생성을 요청하면") {
                val timetable = giveMePerfectCase(id = timeTableId)

                Then("InvalidTimeTableIdException(\"TimeTable ID is empty.\")가 발생한다") {
                    val exception =
                        shouldThrow<InvalidTimeTableIdException> {
                            domainService.create(userId, timetable)
                        }

                    exception.message shouldBe "TimeTable ID is empty."
                }
            }
        }

        Given("빈 문자열 timeTableId를 가진 TimeTable이 주어졌을 때") {
            val userId = UuidGenerator.generate()
            val timeTableId = ""

            When("예약 점유 생성을 요청하면") {
                val timetable = giveMePerfectCase(id = timeTableId)

                Then("InvalidTimeTableIdException(\"TimeTable ID is empty.\")가 발생한다") {
                    val exception =
                        shouldThrow<InvalidTimeTableIdException> {
                            domainService.create(userId, timetable)
                        }

                    exception.message shouldBe "TimeTable ID is empty."
                }
            }
        }

        Given("UUID 형식이 아닌 timeTableId를 가진 TimeTable이 주어졌을 때") {
            val userId = UuidGenerator.generate()
            val timeTableId = Arbitraries.strings().ascii().ofLength(128).sample()

            When("예약 점유 생성을 요청하면") {
                val timetable = giveMePerfectCase(id = timeTableId)

                Then("InvalidTimeTableIdException(\"Time Table ID has invalid format.\")가 발생한다") {
                    val exception =
                        shouldThrow<InvalidTimeTableIdException> {
                            domainService.create(userId, timetable)
                        }

                    exception.message shouldBe "Time Table ID has invalid format."
                }
            }
        }

        // Scenario 3: 이미 점유된 테이블 처리
        Given("다른 사용자에게 이미 점유된 TimeTable이 주어졌을 때") {
            val userId = UuidGenerator.generate()
            val timeTableId = UuidGenerator.generate()
            val timeTableStatus = TableStatus.OCCUPIED
            val timetable = giveMePerfectCase(id = timeTableId, tableStatus = timeTableStatus)
            When("새로운 사용자로 예약 점유 생성을 요청하면") {
                Then("InvalidTimeTableStatusException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidTimeTableStatusException> {
                            domainService.create(userId, timetable)
                        }
                    exception.message shouldBe "This table is not vacant."
                }
            }
        }

        // Scenario 4: 정상 점유 생성
        Given("유효한 userId와 점유되지 않은 유효한 TimeTable이 주어졌을 때") {
            val userId = UuidGenerator.generate()
            val timeTableId = UuidGenerator.generate()
            val timetable = giveMePerfectCase(id = timeTableId)

            When("예약 점유 생성을 요청하면") {
                val result = domainService.create(userId, timetable)

                Then(
                    """
                    TimeTableSnapshot이 반환되고
                    And: TimetableOccupancy가 OCCUPIED 상태로 생성되고
                    And: 점유 시간이 현재 시간으로 설정되고
                    And: 해제 시간은 null이다
                    """.trimIndent(),
                ) {
                    val timetableOccupancy = result.timetableOccupancy
                    result.shouldBeInstanceOf<TimeTableSnapshot>()
                    timetableOccupancy shouldNotBe null
                    timetableOccupancy?.occupiedStatus shouldBe OccupyStatus.OCCUPIED
                    timetableOccupancy?.occupiedDatetime shouldNotBe null
                    timetableOccupancy?.unoccupiedDatetime shouldBe null
                }
            }
        }
    },
) {
    companion object {
        fun giveMePerfectCase(
            id: String?,
            tableStatus: TableStatus = TableStatus.EMPTY,
        ): TimeTable {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val timeTable =
                pureMonkey.giveMeBuilder<TimeTable>()
                    .set("id", id)
                    .set("tableStatus", tableStatus)
                    .sample()

            return timeTable
        }
    }
}
