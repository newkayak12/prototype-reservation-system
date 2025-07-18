package com.reservation.authenticate

import com.navercorp.fixturemonkey.api.jqwik.JavaTypeArbitraryGenerator
import com.navercorp.fixturemonkey.api.jqwik.JqwikPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.authenticate.service.AuthenticateSignInService
import com.reservation.enumeration.AccessStatus
import com.reservation.enumeration.UserStatus.ACTIVATED
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.shared.vo.LockState
import com.reservation.user.shared.vo.Password
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.jqwik.api.Arbitraries
import net.jqwik.api.arbitraries.StringArbitrary
import java.time.LocalDateTime

class SignInSuccessTest : BehaviorSpec(
    {
        val fixtureMonkey =
            FixtureMonkeyFactory.giveMePureMonkey()
                .plugin {
                    JqwikPlugin().javaTypeArbitraryGenerator(
                        object : JavaTypeArbitraryGenerator {
                            override fun strings(): StringArbitrary {
                                return Arbitraries.strings().ofLength(12)
                            }
                        },
                    )
                }
                .build()

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
                    )
                    .set("lockState", LockState(0, null, ACTIVATED))
                    .sample()

            When("로그인 요청을 한다.") {
                val actual = authenticateSignInService.signIn(authenticate, rawPassword)

                Then("성공 플래그 true가 된다.") {
                    actual.isSuccess shouldBe true
                }

                Then("로그인 성공 히스토리 1개 생성이 된다.") {
                    actual.accessHistories shouldHaveSize 1
                    actual.accessHistories[0].accessDetails.accessStatus shouldBe
                        AccessStatus.SUCCESS
                }
            }
        }
    },
)
