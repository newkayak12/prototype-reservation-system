package com.reservation.user.general

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.shared.LoginId
import com.reservation.user.shared.Password
import com.reservation.user.shared.PersonalAttributes
import com.reservation.user.self.User
import com.reservation.user.service.ChangeGeneralUserPasswordService
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChangeGeneralUserPasswordServiceTest : FunSpec(
    {
        val changeGeneralUserPasswordService = ChangeGeneralUserPasswordService()

        test("비밀번호 길이가 8 ~ 18 글자 사이를 만족하고 비밀번호 형식에 만족하여 비밀번호가 변경된다.") {

            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val user =
                User(
                    id = pureMonkey.giveMeOne<String>(),
                    loginId = pureMonkey.giveMeOne<LoginId>(),
                    password = pureMonkey.giveMeOne<Password>(),
                    personalAttributes = pureMonkey.giveMeOne<PersonalAttributes>(),
                    nickname = pureMonkey.giveMeOne<String>(),
                )
            val rawPassword = CommonlyUsedArbitraries.passwordArbitrary.sample()

            val actual = changeGeneralUserPasswordService.changePassword(user, rawPassword)

            PasswordEncoderUtility.matches(rawPassword, actual.userEncodedPassword) shouldBe true
        }
    },
)
