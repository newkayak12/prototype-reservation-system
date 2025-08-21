package com.reservation.menu

import com.reservation.menu.exceptions.InvalidateMenuElementException
import com.reservation.menu.policy.format.CreateMenuForm
import com.reservation.menu.service.CreateMenuDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.string.shouldContain
import net.jqwik.api.Arbitraries
import java.math.BigDecimal
import java.math.RoundingMode.UP

class CreateMenuTest : BehaviorSpec(
    {
        val domainService = CreateMenuDomainService()

        Given("Title이 비어 있는 form이 주어진다.") {
            val titleIsEmpty = perfectCase().copy(title = "")
            When("Menu를 생성할 때") {
                Then("InvalidateMenuElementException와 함께 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            domainService.createMenu(titleIsEmpty)
                        }
                    exception.message shouldContain "Menu Title must not empty."
                }
            }
        }

        Given("Title이 길이가 긴 form이 주어진다.") {
            val exceededSize = 31
            val title = Arbitraries.strings().alpha().ofMinLength(exceededSize).sample()
            val titleIsTooLong = perfectCase().copy(title = title)
            When("Menu를 생성할 때") {
                Then("InvalidateMenuElementException와 함께 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            domainService.createMenu(titleIsTooLong)
                        }
                    exception.message shouldContain "Menu Title has to between"
                }
            }
        }

        Given("Description이 비어 있는 form이 주어진다.") {
            val descriptionIsEmpty = perfectCase().copy(description = "")
            When("Menu를 생성할 때") {
                Then("InvalidateMenuElementException와 함께 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            domainService.createMenu(descriptionIsEmpty)
                        }
                    exception.message shouldContain "Menu Description must not empty."
                }
            }
        }

        Given("Description이이 길이가 긴 form이 주어진다.") {
            val exceededSize = 256
            val description = Arbitraries.strings().alpha().ofMinLength(exceededSize).sample()
            val descriptionIsTooLong = perfectCase().copy(description = description)
            When("Menu를 생성할 때") {
                Then("InvalidateMenuElementException와 함께 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            domainService.createMenu(descriptionIsTooLong)
                        }
                    exception.message shouldContain "Menu Description has to between "
                }
            }
        }

        Given("Price가 음수인 form이 주어진다.") {
            val minSize = 0L
            val price = Arbitraries.bigDecimals().lessThan(BigDecimal.valueOf(minSize)).sample()
            val priceIsUnderZero = perfectCase().copy(price = price)
            When("Menu를 생성할 때") {
                Then("InvalidateMenuElementException와 함께 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            domainService.createMenu(priceIsUnderZero)
                        }
                    exception.message shouldContain "Menu price has to between "
                }
            }
        }

        Given("Price가 999,999,999보다 큰 form이 주어진다.") {
            val maxSize = 999_999_999L
            val price = Arbitraries.bigDecimals().greaterThan(BigDecimal.valueOf(maxSize)).sample()
            val priceIsTooBig = perfectCase().copy(price = price)
            When("Menu를 생성할 때") {
                Then("InvalidateMenuElementException와 함께 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            domainService.createMenu(priceIsTooBig)
                        }
                    exception.message shouldContain "Menu price has to between "
                }
            }
        }

        Given("RestaurantId가 비어있는 form이 주어진다.") {
            val restaurantIdIsEmpty = perfectCase().copy(restaurantId = "")
            When("Menu를 생성할 때") {
                Then("InvalidateMenuElementException와 함께 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateMenuElementException> {
                            domainService.createMenu(restaurantIdIsEmpty)
                        }
                    exception.message shouldContain "Menu-RestaurantId must not empty."
                }
            }
        }

        Given("조건에 부합하는 form이 주어진다.") {
            val perfectCase = perfectCase()
            When("Menu를 생성할 때") {
                val result = domainService.createMenu(perfectCase)
                Then("입력했던 바와 똑같은 Snapshot이 만들어진다.") {

                    result.restaurantId shouldBeEqual perfectCase.restaurantId
                    result.title shouldBeEqual perfectCase.title
                    result.description shouldBeEqual perfectCase.description
                    result.price shouldBeEqual perfectCase.price
                    result.isRepresentative shouldBeEqual perfectCase.isRepresentative
                    result.isRecommended shouldBeEqual perfectCase.isRecommended
                    result.isVisible shouldBeEqual perfectCase.isVisible
                }
            }
        }
    },
) {
    companion object {
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

        fun perfectCase() =
            CreateMenuForm(
                restaurantId = stringArbitrary.sample(),
                title = stringArbitrary.sample(),
                description = stringArbitrary.sample(),
                price = bigDecimalArbitrary.sample().setScale(0, UP),
                isRepresentative = booleanArbitrary.sample(),
                isRecommended = booleanArbitrary.sample(),
                isVisible = booleanArbitrary.sample(),
            )
    }
}
