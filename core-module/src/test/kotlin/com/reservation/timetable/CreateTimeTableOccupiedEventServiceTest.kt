package com.reservation.timetable

import com.reservation.timetable.event.TimeTableOccupiedDomainEvent
import com.reservation.timetable.policy.exceptions.InvalidTimeTableIdException
import com.reservation.timetable.policy.exceptions.InvalidTimeTableOccupancyIdException
import com.reservation.timetable.service.CreateTimeTableOccupiedDomainEventService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import net.jqwik.api.Arbitraries

class CreateTimeTableOccupiedEventServiceTest : BehaviorSpec(
    {
        val domainService = CreateTimeTableOccupiedDomainEventService()

        Given("timeTableId가 emptyString이어서 ") {
            val timetableId = ""
            val occupancyId = Arbitraries.strings().ofLength(128).sample()

            When("timeTable Occupied 이벤트를 생성할 때 ") {
                Then("Validation에 실패하여 InvalidTimeTableIdException이 발생한다.") {
                    shouldThrow<InvalidTimeTableIdException> {
                        domainService.create(timetableId, occupancyId)
                    }
                }
            }
        }

        Given("timeTableId가 Format에 맞지 않아서 ") {
            val timetableId = Arbitraries.strings().ofLength(128).sample()
            val occupancyId = Arbitraries.strings().ofLength(128).sample()

            When("timeTable Occupied 이벤트를 생성할 때 ") {
                Then("Validation에 실패하여 InvalidTimeTableIdException이 발생한다.") {
                    shouldThrow<InvalidTimeTableIdException> {
                        domainService.create(timetableId, occupancyId)
                    }
                }
            }
        }

        Given("timeTableOccupancyId가 emptyString이어서 ") {
            val timetableId = UuidGenerator.generate()
            val occupancyId = ""

            When("timeTable Occupied 이벤트를 생성할 때 ") {
                Then("Validation에 실패하여 InvalidTimeTableIdException이 발생한다.") {
                    shouldThrow<InvalidTimeTableOccupancyIdException> {
                        domainService.create(timetableId, occupancyId)
                    }
                }
            }
        }

        Given("timeTableOccupancyId가 Format에 맞지 않아서 ") {
            val timetableId = UuidGenerator.generate()
            val occupancyId = Arbitraries.strings().ofLength(128).sample()

            When("timeTable Occupied 이벤트를 생성할 때 ") {
                Then("Validation에 실패하여 InvalidTimeTableIdException이 발생한다.") {
                    shouldThrow<InvalidTimeTableOccupancyIdException> {
                        domainService.create(timetableId, occupancyId)
                    }
                }
            }
        }

        Given("timeTableId, timeTableOccupancyId가 조건에 부합하여  ") {
            val timetableId = UuidGenerator.generate()
            val occupancyId = UuidGenerator.generate()

            When("timeTable Occupied 이벤트를 생성하면 ") {
                val event = domainService.create(timetableId, occupancyId)

                Then("TimeTableOccupiedDomainEvent가 발행된다.") {
                    event should beInstanceOf<TimeTableOccupiedDomainEvent>()
                    event.timeTableId shouldBe timetableId
                    event.timeTableOccupancyId shouldBe occupancyId
                }
            }
        }
    },
)
