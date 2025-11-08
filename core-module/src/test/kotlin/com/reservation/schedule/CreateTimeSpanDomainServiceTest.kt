package com.reservation.schedule

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.schedule.exceptions.InvalidateTimeSpanElementException
import com.reservation.schedule.policy.form.CreateTimeSpanForm
import com.reservation.schedule.service.CreateTimeSpanDomainService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import net.jqwik.api.Arbitraries
import java.time.LocalTime

class CreateTimeSpanDomainServiceTest: BehaviorSpec(
    {
        val service = CreateTimeSpanDomainService()

        // restaurantId가 빈 문자열일 때 InvalidateTimeSpanElementException이 발생한다
        Given("restaurantId가 있지만 빈 문자열이고 나머지는 임의의 값일 때"){
            val monkey = FixtureMonkeyFactory.giveMePureMonkey().build();
            val schedule = monkey.giveMeOne<Schedule>()
            val form = monkey.giveMeBuilder<CreateTimeSpanForm>()
                .set("restaurantId", "")
                .sample()

            When("시간을 생성하면 ") {
                Then("InvalidateTimeSpanElementException이 발생한다.") {
                    shouldThrow<InvalidateTimeSpanElementException> {
                        service.create(schedule, form)
                    }
                }
            }
        }

        // restaurantId가 UUID 형식이 아닐 때 InvalidateTimeSpanElementException이 발생한다
        Given("restaurantId가 있지만 UUID 형식이 아니고 나머지는 임의의 값일 때"){
            val monkey = FixtureMonkeyFactory.giveMePureMonkey().build();
            val schedule = monkey.giveMeOne<Schedule>()
            val form = monkey.giveMeBuilder<CreateTimeSpanForm>()
                .set("restaurantId", Arbitraries.strings().sample())
                .sample()

            When("시간을 생성하면 ") {
                Then("InvalidateTimeSpanElementException이 발생한다.") {
                    shouldThrow<InvalidateTimeSpanElementException> {
                        service.create(schedule, form)
                    }
                }
            }
        }
        // 시작시간이 종료시간보다 늦을 때 InvalidateTimeSpanElementException이 발생한다
        Given("시작 시간이 종료 시간보다 늦는 경우에"){
            val monkey = FixtureMonkeyFactory.giveMePureMonkey().build();
            val timestamp = LocalTime.of(14, 0)
            val schedule = monkey.giveMeOne<Schedule>()
            val form = monkey.giveMeBuilder<CreateTimeSpanForm>()
                .set("restaurantId", UuidGenerator.generate())
                .set("startTime", timestamp.plusHours(1))
                .set("endTime", timestamp)
                .sample()

            When("시간을 생성하면 ") {
                Then("InvalidateTimeSpanElementException이 발생한다.") {
                    shouldThrow<InvalidateTimeSpanElementException> {
                        service.create(schedule, form)
                    }
                }
            }
        }
        // 시작시간과 종료시간이 같을 때 InvalidateTimeSpanElementException이 발생한다
        Given("시작 시간이 종료 시간이 같은 경우에"){
            val monkey = FixtureMonkeyFactory.giveMePureMonkey().build();
            val timestamp = LocalTime.of(14, 0)
            val schedule = monkey.giveMeOne<Schedule>()
            val form = monkey.giveMeBuilder<CreateTimeSpanForm>()
                .set("restaurantId", UuidGenerator.generate())
                .set("startTime", timestamp)
                .set("endTime", timestamp)
                .sample()

            When("시간을 생성하면 ") {
                Then("InvalidateTimeSpanElementException이 발생한다.") {
                    shouldThrow<InvalidateTimeSpanElementException> {
                        service.create(schedule, form)
                    }
                }
            }
        }

        // 유효한 restaurantId와 시간 정보로 TimeSpan을 생성할 때 성공한다
        Given("restaurantId는 UUID 시작 시간이 종료 시간보다 빠른 경우에"){
            val monkey = FixtureMonkeyFactory.giveMePureMonkey().build();
            val schedule = monkey.giveMeOne<Schedule>()
            val timestamp = LocalTime.of(14, 0)
            val form = monkey.giveMeBuilder<CreateTimeSpanForm>()
                .set("restaurantId", UuidGenerator.generate())
                .set("startTime", timestamp.minusHours(4))
                .set("endTime", timestamp.plusHours(4))
                .sample()

            When("시간을 생성하면 ") {
                val scheduleSnapshot = service.create(schedule, form)

                Then("생성에 성공한다.") {
                    scheduleSnapshot.timeSpans.size shouldNotBe 0
                }
            }
        }
    }
)
