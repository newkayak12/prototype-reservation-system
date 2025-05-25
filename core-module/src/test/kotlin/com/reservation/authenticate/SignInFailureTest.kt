package com.reservation.authenticate

import com.navercorp.fixturemonkey.ArbitraryBuilder
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.api.introspector.BuilderArbitraryIntrospector
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector
import com.navercorp.fixturemonkey.api.introspector.FailoverIntrospector
import com.navercorp.fixturemonkey.api.jqwik.JavaTypeArbitraryGenerator
import com.navercorp.fixturemonkey.api.jqwik.JqwikPlugin
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.authenticate.service.AuthenticateSignInService
import com.reservation.encrypt.password.PasswordEncoderUtility
import com.reservation.enumeration.AccessStatus
import com.reservation.shared.user.Password
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import net.jqwik.api.Arbitraries
import net.jqwik.api.arbitraries.StringArbitrary
import java.time.LocalDateTime

class SignInFailureTest : BehaviorSpec(
    {
        val fixtureMonkey =
            FixtureMonkey.builder()
                .enableLoggingFail(false)
                .defaultNotNull(true)
                .objectIntrospector(
                    FailoverIntrospector(
                        listOf(
                            ConstructorPropertiesArbitraryIntrospector.INSTANCE,
                            BuilderArbitraryIntrospector.INSTANCE,
                        ),
                    ),
                )
                .plugin {
                    KotlinPlugin()
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
            val rawPassword: String =
                Arbitraries.strings()
                    .ofMinLength(8)
                    .ofMaxLength(12)
                    .ascii()
                    .sample()
            val arbitraryBuilder: ArbitraryBuilder<Authenticate> = fixtureMonkey.giveMeBuilder()
            val authenticate: Authenticate =
                arbitraryBuilder
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
                    actual.accessHistories() shouldHaveSize 1
                    actual.accessHistories()[0].accessDetails.accessStatus shouldBe
                        AccessStatus.FAILURE
                }
            }
        }
    },
)
