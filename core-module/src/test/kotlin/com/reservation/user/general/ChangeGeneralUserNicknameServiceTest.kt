package com.reservation.user.general

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.self.User
import com.reservation.user.service.ChangeUserNicknameDomainService
import com.reservation.user.shared.vo.LoginId
import com.reservation.user.shared.vo.Password
import com.reservation.user.shared.vo.PersonalAttributes
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ChangeGeneralUserNicknameServiceTest : BehaviorSpec(
    {
        Given("사용자가 변경할 닉네임을 입력한다.") {

            val changeUserNicknameDomainService = ChangeUserNicknameDomainService()
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val user =
                User(
                    id = pureMonkey.giveMeOne<String>(),
                    loginId = pureMonkey.giveMeOne<LoginId>(),
                    password = pureMonkey.giveMeOne<Password>(),
                    personalAttributes = pureMonkey.giveMeOne<PersonalAttributes>(),
                    nickname = pureMonkey.giveMeOne<String>(),
                )
            val newNickname = CommonlyUsedArbitraries.nicknameArbitrary.sample()

            When("사용자의 닉네임이 변경된다.") {

                val actual =
                    changeUserNicknameDomainService.changePersonalAttributes(
                        user,
                        newNickname,
                    )

                Then("사용자 닉네임이 변경되고 새로 입력한 닉네임과 같다.") {
                    actual.userNickname shouldBe newNickname
                }
            }
        }
    },
)
