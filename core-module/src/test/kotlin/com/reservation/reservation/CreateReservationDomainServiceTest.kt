package com.reservation.reservation

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.reservation.exceptions.ReservationRestaurantIdException
import com.reservation.reservation.exceptions.ReservationTimeTableIdException
import com.reservation.reservation.exceptions.ReservationTimeTableOccupancyIdException
import com.reservation.reservation.exceptions.ReservationUserIdException
import com.reservation.reservation.policy.format.CreateReservationForm
import com.reservation.reservation.service.CreateReservationDomainService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.equals.shouldBeEqual
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CreateReservationDomainServiceTest : BehaviorSpec(
    {
        val service = CreateReservationDomainService()

        Given("UserId가 비어 있어있는 요청으로 ") {
            val form = perfectCase().copy(userId = "")

            When("예약을 저장할 때") {
                Then("ReservationUserIdException이 발생한다.") {
                    shouldThrow<ReservationUserIdException> {
                        service.createReservation(form)
                    }
                }
            }
        }

        Given("UserId가 UUID가 아닌 포맷 요청으로 ") {
            val form =
                perfectCase()
                    .copy(userId = UuidGenerator.generate() + "-")

            When("예약을 저장할 때") {
                Then("ReservationUserIdException이 발생한다.") {
                    shouldThrow<ReservationUserIdException> {
                        service.createReservation(form)
                    }
                }
            }
        }

        Given("RestaurantId가 비어 있어있는 요청으로 ") {
            val form = perfectCase().copy(restaurantId = "")

            When("예약을 저장할 때") {
                Then("ReservationRestaurantIdException 발생한다.") {
                    shouldThrow<ReservationRestaurantIdException> {
                        service.createReservation(form)
                    }
                }
            }
        }

        Given("RestaurantId가 UUID가 아닌 포맷 요청으로 ") {
            val form =
                perfectCase()
                    .copy(restaurantId = UuidGenerator.generate() + "-")

            When("예약을 저장할 때") {
                Then("ReservationRestaurantIdException 발생한다.") {
                    shouldThrow<ReservationRestaurantIdException> {
                        service.createReservation(form)
                    }
                }
            }
        }

        Given("TimeTableId가 비어 있어있는 요청으로 ") {
            val form = perfectCase().copy(timeTableId = "")

            When("예약을 저장할 때") {
                Then("ReservationTimeTableIdException 발생한다.") {
                    shouldThrow<ReservationTimeTableIdException> {
                        service.createReservation(form)
                    }
                }
            }
        }

        Given("TimeTableId가 UUID가 아닌 포맷 요청으로 ") {
            val form =
                perfectCase()
                    .copy(timeTableId = UuidGenerator.generate() + "-")

            When("예약을 저장할 때") {
                Then("ReservationTimeTableIdException 발생한다.") {
                    shouldThrow<ReservationTimeTableIdException> {
                        service.createReservation(form)
                    }
                }
            }
        }

        Given("TimeTableOccupancyId가 비어 있어있는 요청으로 ") {
            val form = perfectCase().copy(timeTableOccupancyId = "")

            When("예약을 저장할 때") {
                Then("ReservationTimeTableOccupancyIdException 발생한다.") {
                    shouldThrow<ReservationTimeTableOccupancyIdException> {
                        service.createReservation(form)
                    }
                }
            }
        }

        Given("TimeTableOccupancyId가 UUID가 아닌 포맷 요청으로 ") {
            val form =
                perfectCase()
                    .copy(timeTableOccupancyId = UuidGenerator.generate() + "-")

            When("예약을 저장할 때") {
                Then("ReservationTimeTableOccupancyIdException 발생한다.") {
                    shouldThrow<ReservationTimeTableOccupancyIdException> {
                        service.createReservation(form)
                    }
                }
            }
        }

        Given("올바르게 설정된 요청으로 ") {
            val form = perfectCase()

            When("예약을 저장할 때") {
                val snapshot = service.createReservation(form)
                Then("생성이 완료되어 snapshot이 생성된다.") {
                    val booker = snapshot.booker
                    val restaurantInformation = snapshot.restaurantInformation
                    val schedule = snapshot.schedule
                    val occupancy = snapshot.occupancy

                    booker.userId shouldBeEqual form.userId
                    restaurantInformation.restaurantId shouldBeEqual form.restaurantId
                    restaurantInformation.tableNumber shouldBeEqual form.tableNumber
                    restaurantInformation.tableSize shouldBeEqual form.tableSize
                    schedule.timeTableId shouldBeEqual form.timeTableId
                    schedule.date shouldBeEqual form.date
                    schedule.day shouldBeEqual form.day
                    schedule.startTime shouldBeEqual form.startTime
                    schedule.endTime shouldBeEqual form.endTime
                    occupancy.timeTableOccupancyId shouldBeEqual form.timeTableOccupancyId
                    occupancy.occupiedDatetime shouldBeEqual form.occupiedDatetime
                }
            }
        }
    },
) {
    companion object {
        private val pureMonkey = FixtureMonkeyFactory.giveMeJakartaMonkey().build()

        fun perfectCase() =
            CreateReservationForm(
                UuidGenerator.generate(),
                UuidGenerator.generate(),
                UuidGenerator.generate(),
                UuidGenerator.generate(),
                pureMonkey.giveMeOne<LocalDate>(),
                pureMonkey.giveMeOne<LocalDate>().dayOfWeek,
                pureMonkey.giveMeOne<LocalTime>(),
                pureMonkey.giveMeOne<LocalTime>(),
                pureMonkey.giveMeOne<Int>(),
                pureMonkey.giveMeOne<Int>(),
                pureMonkey.giveMeOne<LocalDateTime>(),
            )
    }
}
