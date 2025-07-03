package com.reservation.company

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.company.vo.Brand
import com.reservation.fixture.FixtureMonkeyFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ChangeCompanyTest : BehaviorSpec(
    {
        Given("회사가 주어진다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val company = pureMonkey.giveMeOne<Company>()

            When("회사 홈페이지와 이름을 변경하면") {
                val name = "BBQ"
                val url = "https://bbq.co.kr"
                company.changeBrand(
                    Brand(name, url),
                )

                Then("BBQ, https://bbq.co.kr로 변경된다.") {
                    company.brandName shouldBe name
                    company.brandUrl shouldBe url
                }
            }
        }
    },
)
