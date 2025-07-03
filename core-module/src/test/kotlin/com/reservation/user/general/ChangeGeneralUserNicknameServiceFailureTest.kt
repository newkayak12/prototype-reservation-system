package com.reservation.user.general

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.common.exceptions.InvalidateUserElementException
import com.reservation.user.self.User
import com.reservation.user.service.ChangeUserNicknameService
import com.reservation.user.shared.vo.LoginId
import com.reservation.user.shared.vo.Password
import com.reservation.user.shared.vo.PersonalAttributes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class ChangeGeneralUserNicknameServiceFailureTest : FunSpec(
    {

        test("입력한 닉네임이 5 ~ 12에 맞지 않아 변경되지 않고 예외를 발생시킨다.") {
            val changeUserNicknameService = ChangeUserNicknameService()
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val user =
                User(
                    id = pureMonkey.giveMeOne<String>(),
                    loginId = pureMonkey.giveMeOne<LoginId>(),
                    password = pureMonkey.giveMeOne<Password>(),
                    personalAttributes = pureMonkey.giveMeOne<PersonalAttributes>(),
                    nickname = pureMonkey.giveMeOne<String>(),
                )
            val newNickname = CommonlyUsedArbitraries.nicknameArbitrary.sample().substring(0, 4)

            shouldThrow<InvalidateUserElementException> {

                changeUserNicknameService.changePersonalAttributes(user, newNickname)
            }
        }
    },
)
