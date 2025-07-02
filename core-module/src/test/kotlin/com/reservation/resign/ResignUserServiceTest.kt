package com.reservation.resign

import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.resign.self.service.ResignUserService
import com.reservation.user.shared.LoginId
import com.reservation.user.shared.Password
import com.reservation.user.shared.PersonalAttributes
import com.reservation.user.self.User
import com.reservation.utilities.encrypt.bidirectional.BidirectionalEncryptUtility
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.equals.shouldBeEqual

class ResignUserServiceTest : BehaviorSpec(
    {

        @Suppress("MaxLineLength")
        val secretKey = "af869782f3abde439def4230f2b05bd701f1ab047ec80808d3b84f1c8923506a"
        val bidirectionalUtility = BidirectionalEncryptUtility(secretKey)
        val service = ResignUserService(bidirectionalUtility)

        Given("사용자를 조회한다.") {

            val id = UuidGenerator.generate()
            val loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample()
            val email = CommonlyUsedArbitraries.emailArbitrary.sample()
            val password = CommonlyUsedArbitraries.passwordArbitrary.sample()
            val phoneNumber = CommonlyUsedArbitraries.phoneNumberArbitrary.sample()
            val nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample()

            val user =
                User(
                    id = id,
                    loginId = LoginId(loginId),
                    password = Password(password, null, null),
                    personalAttributes = PersonalAttributes(email, phoneNumber),
                    nickname = nickname,
                )

            val role = user.userRole

            When("사용자를 탈퇴시킨다.") {

                val actual = service.resign(user)
                val decryptActualEmail =
                    bidirectionalUtility.decrypt(
                        actual.encryptedAttributes.encryptedEmail,
                    )
                val decryptActualNickname =
                    bidirectionalUtility.decrypt(
                        actual.encryptedAttributes.encryptedNickname,
                    )
                val decryptActualMobile =
                    bidirectionalUtility.decrypt(
                        actual.encryptedAttributes.encryptedMobile,
                    )
                val decryptActualRole =
                    bidirectionalUtility.decrypt(
                        actual.encryptedAttributes.encryptedRole,
                    )

                Then("탈퇴한 사용자의 이메일을 복호화하면 일치한다.") {
                    email shouldBeEqual decryptActualEmail
                }
                Then("탈퇴한 사용자의 닉네임을 복호화하면 일치한다.") {
                    nickname shouldBeEqual decryptActualNickname
                }
                Then("탈퇴한 사용자의 휴대 전화번호를 복호화하면 일치한다.") {
                    phoneNumber shouldBeEqual decryptActualMobile
                }
                Then("탈퇴한 사용자의 사용자 role을 복호화하면 일치한다.") {
                    role.name shouldBeEqual decryptActualRole
                }
            }
        }
    },
)
