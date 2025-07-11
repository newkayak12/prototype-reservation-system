package com.reservation.restaurant

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.format.CreateRestaurantForm
import com.reservation.restaurant.service.CreateRestaurantService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import net.jqwik.api.Arbitraries
import java.math.BigDecimal

class CreateRestaurantServiceTest : BehaviorSpec(
    {

        val service = CreateRestaurantService()
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val perfectCase =
            pureMonkey.giveMeBuilder<CreateRestaurantForm>()
                .set("name", Arbitraries.strings().ofMaxLength(64).ofMinLength(1).sample())
                .set("introduce", Arbitraries.strings().ofMaxLength(6000).sample())
                .set("phone", CommonlyUsedArbitraries.phoneNumberArbitrary.sample())
                .set("zipCode", CommonlyUsedArbitraries.zipCodeArbitary.sample())
                .set("address", Arbitraries.strings().ofMinLength(5).ofMaxLength(256).sample())
                .set(
                    "latitude",
                    Arbitraries.bigDecimals()
                        .between(BigDecimal.valueOf(33.0), BigDecimal.valueOf(38.7))
                        .sample(),
                )
                .set(
                    "longitude",
                    Arbitraries.bigDecimals()
                        .between(BigDecimal.valueOf(124.0), BigDecimal.valueOf(131.2))
                        .sample(),
                )

        /**
         * Given: 음식점 이름이 비어있는 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 음식점 이름이 비어 있어서 실패한다.
         */
        Given(" 음식점 이름이 비어있는 생성 요청이 들어온다.") {

            val form =
                perfectCase
                    .set("name", "")
                    .sample()

            When("음식점을 생성할 때") {
                Then("음식점 이름이 비어 있어서 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "restaurant name is empty"
                }
            }
        }

        /**
         * Given: 음식점 이름이 64글자 이상인 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 음식점 이름이 비어있어서 실패한다.
         */
        Given(" 음식점 이름이 64글자 이상인 생성 요청이 들어온다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val form =
                perfectCase
                    .set("name", Arbitraries.strings().ofMinLength(65).sample())
                    .sample()

            When("음식점을 생성할 때") {
                Then("음식점 이름이 비어있어서 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "restaurant name is too long"
                }
            }
        }

        /**
         * Given: 음식점 소개가 6000자를 넘기는 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 음식점 소개 길이 생성에 실패한다.
         */
        Given(" 음식점 소개가 6000자를 넘기는 생성 요청이 들어온다.") {

            val form =
                perfectCase
                    .set("introduce", Arbitraries.strings().ofMinLength(6001).sample())
                    .sample()
            When("음식점을 생성할 때") {
                Then("음식점 소개 길이 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Introduction is too long"
                }
            }
        }

        /**
         * Given: 전화번호가 비어있는 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 음식점 전화번호가 비어있어 생성에 실패한다.
         */
        Given(" 전화번호가 비어있는 생성 요청이 들어온다.") {

            val form =
                perfectCase
                    .set("phone", "")
                    .sample()
            When("음식점을 생성할 때") {
                Then("음식점 전화번호가 비어있어 생성에 실패한다") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Phone number is empty."
                }
            }
        }

        /**
         * Given: 전화번호 길이가 11 ~ 13자를 만족하지 못하는 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 음식점 전화번호 길이 제한에 위배되어 생성에 실패한다.
         */
        Given(" 전화번호 길이가 13자를 넘는 조건을 만족하지 못하는 생성 요청이 들어온다.") {

            val form =
                perfectCase
                    .set("phone", CommonlyUsedArbitraries.phoneNumberArbitrary.sample() + 1)
                    .sample()
            When("음식점을 생성할 때") {
                Then("음식점 전화번호 길이 제한에 위배되어 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Phone number length is between "
                }
            }
        }

        Given(" 전화번호 길이가 11자를 못 넘는 조건을 만족하지 못하는 생성 요청이 들어온다.") {

            val form =
                perfectCase
                    .set(
                        "phone",
                        CommonlyUsedArbitraries.phoneNumberArbitrary.sample()
                            .substring(IntRange(0, 9)),
                    )
                    .sample()
            When("음식점을 생성할 때") {
                Then("음식점 전화번호 길이 제한에 위배되어 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Phone number length is between "
                }
            }
        }

        /**
         * Given: 음식점 전화번호의 형태가 전화/ 휴대전화 형식에 맞지 않는 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 음식점 점화번호 형식 제한에 위배되어 생성에 실패한다.
         */
        Given(" 음식점 전화번호의 형태가 전화/휴대전화 형식에 맞지 않는 요청이 들어온다.") {

            val form =
                perfectCase
                    .set(
                        "phone",
                        CommonlyUsedArbitraries.phoneNumberArbitrary.sample()
                            .replaceFirst(Regex("01[016789]"), "000"),
                    )
                    .sample()
            When("음식점을 생성할 때") {
                Then("음식점 점화번호 형식 제한에 위배되어 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Invalid phone format"
                }
            }
        }

        /**
         * Given: 주소가 비어있는 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 주소가 비어있어 형식 제한에 위배되어 생성에 실패한다.
         */
        Given(" 주소가 비어있는 생성 요청이 들어온다.") {

            val form =
                perfectCase
                    .set("address", "")
                    .sample()
            When("음식점을 생성할 때") {
                Then("주소가 비어있어 형식 제한에 위배되어 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Restaurant Address is Empty"
                }
            }
        }

        /**
         * Given: 주소 길이가 256을 초과하는 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 주소가 너무 길어서 생성에 실패한다.
         */
        Given(" 주소 길이가 256을 초과하는 생성 요청이 들어온다.") {

            val form =
                perfectCase
                    .set("address", Arbitraries.strings().ofMinLength(257).sample())
                    .sample()
            When("음식점을 생성할 때") {
                Then("주소가 너무 길어서 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Address length should be shorter than"
                }
            }
        }

        /**
         * Given: 우편번호 길이가 올바르지 못한 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 우편번호 길이가 올바르지 않으므로 생성에 실패한다.
         */
        Given(" 우편번호 길이가 올바르지 못한 생성 요청이 들어온다.") {

            val form =
                perfectCase
                    .set("zipCode", Arbitraries.strings().ofMinLength(5).sample())
                    .sample()
            When("음식점을 생성할 때") {
                Then("우편번호 길이가 올바르지 않으므로 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Invalid zipcode length"
                }
            }
        }

        /**
         * Given: 우편번호가 형식에 맞지 않는다.
         * When: 음식점을 생성할 때
         * Then: 우편번호 형식에 맞지 않으므로 제한되어 생성에 실패한다.
         */
        Given(" 우편번호가 형식에 맞지 않는다.") {

            val form =
                perfectCase
                    .set(
                        "zipCode",
                        Arbitraries.strings()
                            .ofMinLength(5)
                            .ofMaxLength(5)
                            .alpha()
                            .sample(),
                    )
                    .sample()
            When("음식점을 생성할 때") {
                Then("우편번호 형식에 맞지 않으므로 제한되어 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Invalid zipcode format"
                }
            }
        }

        /**
         * Given: 위경도 형식에 맞지 않는 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 잘못된 위경도 형식이므로 생성에 실패한다.
         */
        Given(" 위경도 형식에 맞지 않는 요청이 들어온다.") {

            val form =
                perfectCase
                    .set(
                        "latitude",
                        BigDecimal.ZERO,
                    )
                    .set(
                        "longitude",
                        BigDecimal.ZERO,
                    )
                    .sample()
            When("음식점을 생성할 때") {
                Then("우편번호 형식에 맞지 않으므로 제한되어 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Invalid coordinate format"
                }
            }
        }

        /**
         * Given: 위경도 값이 우리나라를 벗어나는 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 우리나라를 벗어나는 요청이므로 정책적으로 위배되어 생성에 실패한다.
         */
        Given(" 위경도 값이 우리나라를 벗어나는 요청이 들어온다.") {
            val form =
                perfectCase
                    .set(
                        "latitude",
                        BigDecimal.ZERO,
                    )
                    .set(
                        "longitude",
                        BigDecimal.ZERO,
                    )
                    .sample()
            When("음식점을 생성할 때") {
                Then("우편번호 형식에 맞지 않으므로 제한되어 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Coordinate is out of range"
                }
            }
        }
    },
)
