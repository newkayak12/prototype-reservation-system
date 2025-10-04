package com.reservation.schedule

import com.reservation.schedule.exceptions.InvalidateScheduleElementException
import com.reservation.schedule.service.CreateScheduleDomainService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class CreateScheduleDomainServiceTest : BehaviorSpec(
    {
        val service = CreateScheduleDomainService()

        Given("restaurantId가 EmptyString일 때 ") {
            val restaurantId = ""
            When("스케쥴 생성을 요청하면") {
                Then("InvalidateRestaurantElementException가 발생하며 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateScheduleElementException> {
                            service.create(restaurantId)
                        }

                    exception.message shouldContain "restaurant id must not be null"
                }
            }
        }

        Given("restaurantId가 UUID 형식이 아닐 때 ") {
            val restaurantId = "GENERATED_UUID"
            When("스케쥴 생성을 요청하면") {
                Then("InvalidateRestaurantElementException가 발생하며 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateScheduleElementException> {
                            service.create(restaurantId)
                        }

                    exception.message shouldContain "Invalid ID Format"
                }
            }
        }

        Given("restaurantId가 올바른 UUID 일 때") {
            val restaurantId = UuidGenerator.generate()
            When("스케쥴 생성을 요청하면") {
                val result = service.create(restaurantId)
                Then("스케쥴 등록에 성공한다.") {
                    result shouldNotBe null
                    result.restaurantId shouldBeEqual restaurantId
                }
            }
        }
    },
)
