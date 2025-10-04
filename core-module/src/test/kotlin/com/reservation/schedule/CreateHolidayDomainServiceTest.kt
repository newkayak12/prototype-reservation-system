package com.reservation.schedule

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.schedule.exceptions.InvalidateHolidayElementException
import com.reservation.schedule.policy.form.CreateHolidayForm
import com.reservation.schedule.service.CreateHolidayDomainService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.string.shouldContain
import net.jqwik.api.Arbitraries
import java.time.LocalDate

class CreateHolidayDomainServiceTest : BehaviorSpec(
    {
        val service = CreateHolidayDomainService()

        Given("restaurantId가 비어있는 요청으로") {
            val schedule = giveMeSchedule()
            val form = giveMePerfectCase().copy(restaurantId = "")

            When("휴일 생성 요청을 보내면 ") {
                Then("InvalidateHolidayElementException를 반환하며 실패한다.") {
                    val exception = shouldThrow<InvalidateHolidayElementException> {
                        service.create(schedule, form)
                    }

                    exception.message shouldContain "restaurant id must not be null"
                }
            }
        }

        Given("restaurantId가 UUID 형식이 아닌 요청으로") {
            val schedule = giveMeSchedule()
            val form = giveMePerfectCase().copy(
                restaurantId = Arbitraries.strings().ofMinLength(10).sample()
            )

            When("휴일 생성 요청을 보내면 ") {
                Then("InvalidateHolidayElementException를 반환하며 실패한다.") {
                    val exception = shouldThrow<InvalidateHolidayElementException> {
                        service.create(schedule, form)
                    }

                    exception.message shouldContain "Invalid ID Format"
                }
            }
        }

        Given("date가 없는 요청으로") {
            val schedule = giveMeSchedule()
            val form = giveMePerfectCase().copy(
                date = LocalDate.now().minusDays(10)
            )

            When("휴일 생성 요청을 보내면 ") {
                Then("InvalidateHolidayElementException를 반환하며 실패한다.") {
                    val exception = shouldThrow<InvalidateHolidayElementException> {
                        service.create(schedule, form)
                    }

                    exception.message shouldContain "date must not be passed"
                }
            }
        }

        Given("정상적인 요청으로") {
            val schedule = giveMeSchedule()
            val form = giveMePerfectCase()

            When("휴일 생성 요청을 보내면 ") {
                val snapshot = service.create(schedule, form)
                Then("휴일 생성에 성공한다.") {
                    val count = snapshot.holidays.count {
                        it.id == null
                        it.restaurantId == form.restaurantId
                        it.date == form.date
                    }

                    count shouldBeGreaterThanOrEqual 1
                }
            }
        }


    }
) {
    companion object {
        private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

        private fun giveMeSchedule(): Schedule = pureMonkey.giveMeOne<Schedule>()

        private fun giveMePerfectCase(): CreateHolidayForm = CreateHolidayForm(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.now().plusDays(1)
        )
    }
}
