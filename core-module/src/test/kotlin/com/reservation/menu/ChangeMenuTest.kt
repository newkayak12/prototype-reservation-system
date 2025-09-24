package com.reservation.menu

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.menu.exceptions.InvalidateMenuElementException
import com.reservation.menu.policy.format.ChangeMenuForm
import com.reservation.menu.service.ChangeMenuDomainService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import net.jqwik.api.Arbitraries
import java.math.BigDecimal
import java.math.RoundingMode.UP

class ChangeMenuTest : BehaviorSpec(
    {
        val service = ChangeMenuDomainService()

        Given("메뉴의 ID가 비어있는 수정 요청이 전달된다.") {
            val request = giveMePerfectCase().copy(id = "")

            When("이 요청으로 바탕으로 수정을 진행하면") {
                Then("ID가 없어 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Menu id must not be empty"
                }
            }
        }

        Given("메뉴의 ID가 UUID 형식이 아닌 수정 요청이 전달된다.") {
            val request = giveMePerfectCase().copy(restaurantId = stringArbitrary.sample())

            When("이 요청으로 바탕으로 수정을 진행하면") {
                Then("ID가 없어 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Invalid Menu-RestaurantId format."
                }
            }
        }
//
        Given("메뉴의 레스토랑 ID가 비어있는 수정 요청이 전달된다.") {
            val request = giveMePerfectCase().copy(restaurantId = "")

            When("이 요청으로 바탕으로 수정을 진행하면") {
                Then("ID가 없어 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Menu-RestaurantId must not empty"
                }
            }
        }

        Given("메뉴의 레스토랑 ID가 UUID 형식이 아닌 수정 요청이 전달된다.") {
            val request = giveMePerfectCase().copy(restaurantId = stringArbitrary.sample())

            When("이 요청으로 바탕으로 수정을 진행하면") {
                Then("ID가 없어 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Invalid Menu-RestaurantId format"
                }
            }
        }

        Given("메뉴 이름이 비어있는 수정 요청이 전달된다.") {
            val request = giveMePerfectCase().copy(title = "")

            When("이 요청을 바탕으로 수정을 진행하면") {
                Then("메뉴 이름의 정책과 부합하지 않아서 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Menu Title must not empty."
                }
            }
        }

        Given("메뉴 이름이 30자 이상인 수정 요청이 전달된다.") {
            val title = Arbitraries.strings().ofMinLength(31).sample()
            val request = giveMePerfectCase().copy(title = title)

            When("이 요청을 바탕으로 수정을 진행하면") {
                Then("메뉴 이름의 정책과 부합하지 않아서 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Menu Title has to"
                }
            }
        }

        Given("메뉴 설명이 비어있는 수정 요청이 전달된다.") {
            val request = giveMePerfectCase().copy(description = "")

            When("이 요청을 바탕으로 수정을 진행하면") {
                Then("메뉴 설명이 비어있어 정책에 부합하지 않아서 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Menu Description must not empty."
                }
            }
        }
//
        Given("메뉴 설명이 255자 이상인 수정 요청이 전달된다.") {
            val description = Arbitraries.strings().ofMinLength(256).sample()
            val request = giveMePerfectCase().copy(description = description)

            When("이 요청을 바탕으로 수정을 진행하면") {
                Then("메뉴 설명이 길이가 255를 넘어서 정책에 부합하지 않아서 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Menu Description has to between"
                }
            }
        }
//
        Given("메뉴 가격이 음수인 수정 요청이 전달된다.") {
            val price =
                Arbitraries.bigDecimals()
                    .lessThan(BigDecimal.ZERO)
                    .sample()
            val request = giveMePerfectCase().copy(price = price)

            When("이 요청을 바탕으로 수정을 진행하면") {
                Then("메뉴 가격이 음수이므로 정책에 부합하지 않아서 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Menu price has to between"
                }
            }
        }
//
        Given("메뉴 가격이 999_999_999 이상인 수정 요청이 전달된다.") {
            val price =
                Arbitraries.bigDecimals()
                    .greaterThan(BigDecimal.valueOf(999_999_999L))
                    .sample()
            val request = giveMePerfectCase().copy(price = price)

            When("이 요청을 바탕으로 수정을 진행하면") {
                Then("메뉴 가격이 음수이므로 정책에 부합하지 않아서 InvalidateMenuElementException가 발생한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            service.updateMenu(menu, request)
                        }

                    exception.shouldBeInstanceOf<InvalidateMenuElementException>()
                    exception.message shouldContain "Menu price has to between"
                }
            }
        }
//
    },
) {
    companion object {
        private val jakartaMonkey = FixtureMonkeyFactory.giveMeJakartaMonkey().build()

        private val stringArbitrary =
            Arbitraries.strings()
                .withCharRange('A', 'Z')
                .ofMinLength(1)
                .ofMaxLength(30)
        private val bigDecimalArbitrary =
            Arbitraries.bigDecimals()
                .greaterThan(BigDecimal.ZERO)
                .lessThan(BigDecimal.valueOf(999999999L))
                .greaterThan(BigDecimal.ZERO)

        private val booleanArbitrary = Arbitraries.of(true, false)

        private val menu = jakartaMonkey.giveMeOne<Menu>()

        fun giveMePerfectCase() =
            ChangeMenuForm(
                id = UuidGenerator.generate(),
                restaurantId = UuidGenerator.generate(),
                title = stringArbitrary.sample(),
                description = stringArbitrary.sample(),
                price = bigDecimalArbitrary.sample().setScale(0, UP),
                isRepresentative = booleanArbitrary.sample(),
                isRecommended = booleanArbitrary.sample(),
                isVisible = booleanArbitrary.sample(),
            )
    }
}
