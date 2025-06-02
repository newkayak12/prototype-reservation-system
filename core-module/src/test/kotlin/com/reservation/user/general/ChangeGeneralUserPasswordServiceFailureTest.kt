package com.reservation.user.general

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.user.common.exceptions.InvalidateUserElementException
import com.reservation.user.self.User
import com.reservation.user.service.ChangeGeneralUserPasswordService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class ChangeGeneralUserPasswordServiceFailureTest : FunSpec(
    {
        val changeGeneralUserPasswordService = ChangeGeneralUserPasswordService()

        test("비밀번호 길이가 8 ~ 18 글자 사이를 만족하지 못해서 실패한다.") {

            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val user =
                User(
                    id = pureMonkey.giveMeOne<String>(),
                    loginId = pureMonkey.giveMeOne<LoginId>(),
                    password = pureMonkey.giveMeOne<Password>(),
                    personalAttributes = pureMonkey.giveMeOne<PersonalAttributes>(),
                    nickname = pureMonkey.giveMeOne<String>(),
                )
            val rawPassword = pureMonkey.giveMeOne<String>().substring(0, 7)

            shouldThrow<InvalidateUserElementException> {
                changeGeneralUserPasswordService.changePassword(user, rawPassword)
            }
        }

        test("비밀번호 길이가 8 ~ 18 글자 사이를 만족하지만 ") {

            val pureMonkey =
                FixtureMonkeyFactory.giveMePureMonkey().build()

            val user =
                User(
                    id = pureMonkey.giveMeOne<String>(),
                    loginId = pureMonkey.giveMeOne<LoginId>(),
                    password = pureMonkey.giveMeOne<Password>(),
                    personalAttributes = pureMonkey.giveMeOne<PersonalAttributes>(),
                    nickname = pureMonkey.giveMeOne<String>(),
                )
            val rawPassword = pureMonkey.giveMeOne<String>()

            shouldThrow<InvalidateUserElementException> {
                changeGeneralUserPasswordService.changePassword(user, rawPassword)
            }
        }
    },
)
