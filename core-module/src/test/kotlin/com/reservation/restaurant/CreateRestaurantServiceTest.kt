package com.reservation.restaurant

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.restaurant.exceptions.InvalidateRestaurantElementException
import com.reservation.restaurant.policy.format.CreateRestaurantForm
import com.reservation.restaurant.policy.format.RestaurantWorkingDayForm
import com.reservation.restaurant.service.CreateRestaurantDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.string.shouldContain
import net.jqwik.api.Arbitraries
import java.math.BigDecimal

class CreateRestaurantServiceTest : BehaviorSpec(
    {

        val service = CreateRestaurantDomainService()

        /**
         * Given: 음식점 이름이 비어있는 생성 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 음식점 이름이 비어 있어서 실패한다.
         */
        Given(" 음식점 이름이 비어있는 생성 요청이 들어온다.") {

            val form = perfectCase().copy(name = "")
            When("음식점을 생성할 때 ") {
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
         * Then: 음식점 이름이 너무 길어서 실패한다.
         */
        Given(" 음식점 이름이 64글자 이상인 생성 요청이 들어온다.") {

            val form =
                perfectCase().copy(name = Arbitraries.strings().ofMinLength(64).sample() + "!")
            When("음식점을 생성할 때 ") {
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
                perfectCase().copy(introduce = Arbitraries.strings().ofMinLength(6001).sample())
            When("음식점을 생성할 때 ") {
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

            val form = perfectCase().copy(phone = "")
            When("음식점을 생성할 때 ") {
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
                perfectCase().copy(
                    phone = CommonlyUsedArbitraries.phoneNumberArbitrary.sample() + "123",
                )
            When("음식점을 생성할 때 ") {
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
                perfectCase()
                    .copy(
                        phone =
                            CommonlyUsedArbitraries.phoneNumberArbitrary.sample()
                                .substring(IntRange(0, 9)),
                    )
            When("음식점을 생성할 때 ") {
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
                perfectCase()
                    .copy(
                        phone =
                            CommonlyUsedArbitraries.phoneNumberArbitrary.sample()
                                .replaceFirst(Regex("01[016789]"), "000"),
                    )
            When("음식점을 생성할 때 ") {
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

            val form = perfectCase().copy(address = "")
            When("음식점을 생성할 때 ") {
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
                perfectCase()
                    .copy(address = Arbitraries.strings().ofMinLength(257).sample() + "T")
            When("음식점을 생성할 때 ") {
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
                perfectCase().copy(zipCode = Arbitraries.strings().ofMinLength(6).sample())
            When("음식점을 생성할 때 ") {
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
                perfectCase().copy(
                    zipCode =
                        Arbitraries.strings()
                            .ofMinLength(5)
                            .ofMaxLength(5)
                            .alpha()
                            .sample(),
                )
            When("음식점을 생성할 때 ") {
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

            val point = BigDecimal.valueOf(0.1 - 0.000000000000011)
            val form =
                perfectCase().copy(
                    latitude =
                        BigDecimal.valueOf(
                            Arbitraries.doubles().greaterThan(0.0).sample(),
                        ).add(point),
                    longitude =
                        BigDecimal.valueOf(
                            Arbitraries.doubles().greaterThan(0.0).sample(),
                        ).add(point),
                )

            When("음식점을 생성할 때 ") {
                Then("잘못된 위경도 형식이므로 생성에 실패한다.") {
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
                perfectCase().copy(
                    latitude = BigDecimal.ZERO,
                    longitude = BigDecimal.ZERO,
                )
            When("음식점을 생성할 때 ") {
                Then("우리나라를 벗어나는 요청이므로 정책적으로 위배되어 생성에 실패한다.") {
                    val exception =
                        shouldThrow<InvalidateRestaurantElementException> {
                            service.create(form)
                        }

                    exception.message shouldContain "Coordinate is out of range"
                }
            }
        }

        /**
         * Given: 필수 항목에 대해서 통제 범위 내의 올바른 요청이 들어온다.
         * When: 음식점을 생성할 때
         * Then: 요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.
         */
        Given("올바른 요청이 들어온다.") {
            val request = perfectCase()
            When("음식점을 생성할 때") {
                val result = service.create(request)
                Then("요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.") {
                    request.companyId shouldBeEqual result.companyId
                    request.name shouldBeEqual result.name
                    request.introduce shouldBeEqual result.introduce
                    request.phone shouldBeEqual result.phone
                    request.zipCode shouldBeEqual result.zipCode
                    request.address shouldBeEqual result.address
                    request.detail shouldBeEqual result.detail
                    request.latitude shouldBeEqual result.latitude
                    request.longitude shouldBeEqual result.longitude
                }
            }
        }

        /**
         * Given: 필수 항목에 대해서 통제 범위 내의 올바른 요청이 들어온다. 추가적으로 영업일이 추가된다.
         * When: 음식점을 생성할 때
         * Then: 요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.
         */
        Given("올바른 요청과 영업일이 들어온다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val workingDays = pureMonkey.giveMe<RestaurantWorkingDayForm>(7).distinctBy { it.day }

            val request = perfectCase().copy(workingDays = workingDays)

            When("음식점을 생성할 때") {
                val result = service.create(request)
                Then("요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.") {
                    result.companyId shouldBeEqual request.companyId
                    result.name shouldBeEqual request.name
                    result.introduce shouldBeEqual request.introduce
                    result.phone shouldBeEqual request.phone
                    result.zipCode shouldBeEqual request.zipCode
                    result.address shouldBeEqual request.address
                    result.detail shouldBeEqual request.detail
                    result.latitude shouldBeEqual request.latitude
                    result.longitude shouldBeEqual request.longitude
                    result.workingDays.map { it.day } shouldBeEqual workingDays.map { it.day }
                }
            }
        }

        /**
         * Given: 필수 항목에 대해서 통제 범위 내의 올바른 요청이 들어온다. 추가적으로 사진이 추가된다.
         * When: 음식점을 생성할 때
         * Then: 요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.
         */
        Given("올바른 요청과 사진이 들어온다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val photos = pureMonkey.giveMe<String>(7)

            val request = perfectCase().copy(photos = photos)

            When("음식점을 생성할 때") {
                val result = service.create(request)
                Then("요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.") {
                    result.companyId shouldBeEqual request.companyId
                    result.name shouldBeEqual request.name
                    result.introduce shouldBeEqual request.introduce
                    result.phone shouldBeEqual request.phone
                    result.zipCode shouldBeEqual request.zipCode
                    result.address shouldBeEqual request.address
                    result.detail shouldBeEqual request.detail
                    result.latitude shouldBeEqual request.latitude
                    result.longitude shouldBeEqual request.longitude
                    result.photos.map { it.url } shouldBeEqual photos
                }
            }
        }

        /**
         * Given: 필수 항목에 대해서 통제 범위 내의 올바른 요청이 들어온다. 추가적으로 태그가 추가된다.
         * When: 음식점을 생성할 때
         * Then: 요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.
         */
        Given("올바른 요청과 태그가 들어온다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val categories = pureMonkey.giveMe<Long>(7).stream().distinct().toList()

            val request = perfectCase().copy(tags = categories)

            When("음식점을 생성할 때") {
                val result = service.create(request)
                Then("요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.") {
                    result.companyId shouldBeEqual request.companyId
                    result.name shouldBeEqual request.name
                    result.introduce shouldBeEqual request.introduce
                    result.phone shouldBeEqual request.phone
                    result.zipCode shouldBeEqual request.zipCode
                    result.address shouldBeEqual request.address
                    result.detail shouldBeEqual request.detail
                    result.latitude shouldBeEqual request.latitude
                    result.longitude shouldBeEqual request.longitude
                    result.tags shouldBeEqual categories
                }
            }
        }

        /**
         * Given: 필수 항목에 대해서 통제 범위 내의 올바른 요청이 들어온다. 추가적으로 국가가 추가된다.
         * When: 음식점을 생성할 때
         * Then: 요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.
         */
        Given("올바른 요청과 국가가 들어온다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val categories = pureMonkey.giveMe<Long>(7).stream().distinct().toList()

            val request = perfectCase().copy(nationalities = categories)

            When("음식점을 생성할 때") {
                val result = service.create(request)
                Then("요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.") {
                    result.companyId shouldBeEqual request.companyId
                    result.name shouldBeEqual request.name
                    result.introduce shouldBeEqual request.introduce
                    result.phone shouldBeEqual request.phone
                    result.zipCode shouldBeEqual request.zipCode
                    result.address shouldBeEqual request.address
                    result.detail shouldBeEqual request.detail
                    result.latitude shouldBeEqual request.latitude
                    result.longitude shouldBeEqual request.longitude
                    result.nationalities shouldBeEqual categories
                }
            }
        }

        /**
         * Given: 필수 항목에 대해서 통제 범위 내의 올바른 요청이 들어온다. 추가적으로 음식 태그가 추가된다.
         * When: 음식점을 생성할 때
         * Then: 요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.
         */
        Given("올바른 요청과 음식 태그가 들어온다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val categories = pureMonkey.giveMe<Long>(7).stream().distinct().toList()

            val request = perfectCase().copy(cuisines = categories)

            When("음식점을 생성할 때") {
                val result = service.create(request)
                Then("요청에 따른 생성이 완료되고 각 항목들이 모두 일치한다.") {
                    result.companyId shouldBeEqual request.companyId
                    result.name shouldBeEqual request.name
                    result.introduce shouldBeEqual request.introduce
                    result.phone shouldBeEqual request.phone
                    result.zipCode shouldBeEqual request.zipCode
                    result.address shouldBeEqual request.address
                    result.detail shouldBeEqual request.detail
                    result.latitude shouldBeEqual request.latitude
                    result.longitude shouldBeEqual request.longitude
                    result.cuisines shouldBeEqual categories
                }
            }
        }
    },
) {
    companion object {
        private val MAX_LATITUDE = BigDecimal.valueOf(38.7)
        private val MAX_LONGITUDE = BigDecimal.valueOf(131.2)

        private val MIN_LATITUDE = BigDecimal.valueOf(33.0)
        private val MIN_LONGITUDE = BigDecimal.valueOf(124.0)

        fun perfectCase(): CreateRestaurantForm {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            return pureMonkey.giveMeBuilder<CreateRestaurantForm>()
                .setLazy(
                    "name",
                ) { Arbitraries.strings().ofMaxLength(63).ofMinLength(9).sample() + "T" }
                .setLazy("introduce") { Arbitraries.strings().ofMaxLength(5999).sample() + "T" }
                .setLazy("phone") { CommonlyUsedArbitraries.phoneNumberArbitrary.sample() }
                .setLazy("zipCode") { CommonlyUsedArbitraries.zipCodeArbitary.sample() }
                .setLazy(
                    "address",
                ) { Arbitraries.strings().ofMinLength(10).ofMaxLength(254).sample() + "T" }
                .setLazy("latitude") {
                    Arbitraries.bigDecimals().between(MIN_LATITUDE, MAX_LATITUDE).sample()
                }
                .setLazy("longitude") {
                    Arbitraries.bigDecimals().between(MIN_LONGITUDE, MAX_LONGITUDE).sample()
                }
                .setLazy("workingDays") { listOf<RestaurantWorkingDayForm>() }
                .setLazy("photos") { listOf<String>() }
                .setLazy("tags") { listOf<Long>() }
                .setLazy("nationalities") { listOf<Long>() }
                .setLazy("cuisines") { listOf<Long>() }
                .sample()
        }
    }
}
