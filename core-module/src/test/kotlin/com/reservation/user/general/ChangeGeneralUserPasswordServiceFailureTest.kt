package com.reservation.user.general

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.common.exceptions.InvalidateUserElementException
import com.reservation.user.common.exceptions.UseSamePasswordAsBeforeException
import com.reservation.user.self.User
import com.reservation.user.service.ChangeGeneralUserPasswordDomainService
import com.reservation.user.shared.vo.LoginId
import com.reservation.user.shared.vo.Password
import com.reservation.user.shared.vo.PersonalAttributes
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class ChangeGeneralUserPasswordServiceFailureTest : FunSpec(
    {
        val changeGeneralUserPasswordDomainService = ChangeGeneralUserPasswordDomainService()

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
            val rawPassword = CommonlyUsedArbitraries.passwordArbitrary.sample().substring(0, 7)

            shouldThrow<InvalidateUserElementException> {
                changeGeneralUserPasswordDomainService.changePassword(user, rawPassword)
            }
        }

        test("비밀번호 길이가 8 ~ 18 글자 사이를 만족하지만 형식에 맞지 않는다.") {

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
                changeGeneralUserPasswordDomainService.changePassword(user, rawPassword)
            }
        }

        test("비밀번호가 이전에 사용한 비밀번호와 같다.") {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val rawPassword = CommonlyUsedArbitraries.passwordArbitrary.sample()

            val user =
                User(
                    id = pureMonkey.giveMeOne<String>(),
                    loginId = pureMonkey.giveMeOne<LoginId>(),
                    password =
                        Password(
                            PasswordEncoderUtility.encode(rawPassword),
                            null,
                            null,
                        ),
                    personalAttributes = pureMonkey.giveMeOne<PersonalAttributes>(),
                    nickname = pureMonkey.giveMeOne<String>(),
                )

            shouldThrow<UseSamePasswordAsBeforeException> {
                changeGeneralUserPasswordDomainService.changePassword(user, rawPassword)
            }
        }
    },
)
