package com.reservation.authenticate

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.authenticate.service.AuthenticateSignInService
import com.reservation.enumeration.AccessStatus
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.shared.vo.Password
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.jqwik.api.Arbitraries
import java.time.LocalDateTime

class SignInFailureWIthPasswordTest : BehaviorSpec(
    {
        val authenticateSignInService = AuthenticateSignInService()

        Given("올바른 Authenticate와 사용자가 입력한 패스워드를 제공 받고") {
            var rawPassword: String =
                Arbitraries.strings()
                    .ofMinLength(8)
                    .ofMaxLength(12)
                    .ascii()
                    .sample()

            if (rawPassword.toByteArray().size >= 72) {
                rawPassword = rawPassword.slice(IntRange(0, 7))
            }

            val authenticate: Authenticate =
                FixtureMonkeyFactory.giveMePureMonkey().build()
                    .giveMeBuilder<Authenticate>()
                    .set(
                        "password",
                        Password(
                            PasswordEncoderUtility.encode(rawPassword),
                            null,
                            LocalDateTime.now(),
                        ),
                    ).sample()

            When("잘못된 비밀번호로 로그인 요청을 한다.") {
                val actual =
                    authenticateSignInService.signIn(
                        authenticate,
                        rawPassword + "1",
                    )

                Then("성공 플래그 false 된다.") {
                    actual.isSuccess shouldBe false
                }

                Then("로그인 실패 히스토리 1개 생성이 된다.") {
                    actual.accessHistories shouldHaveSize 1
                    actual.accessHistories[0].accessDetails.accessStatus shouldBe
                        AccessStatus.FAILURE
                }
            }
        }
    },
)
