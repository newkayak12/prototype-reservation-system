package com.reservation.authenticate

import com.navercorp.fixturemonkey.ArbitraryBuilder
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.api.introspector.BeanArbitraryIntrospector
import com.navercorp.fixturemonkey.api.introspector.BuilderArbitraryIntrospector
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector
import com.navercorp.fixturemonkey.api.introspector.FailoverIntrospector
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector
import com.navercorp.fixturemonkey.api.jqwik.JavaTypeArbitraryGenerator
import com.navercorp.fixturemonkey.api.jqwik.JqwikPlugin
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
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

class SignInSuccessTest : BehaviorSpec(
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
                            FieldReflectionArbitraryIntrospector.INSTANCE,
                            BeanArbitraryIntrospector.INSTANCE,
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
            val rawPassword: String = fixtureMonkey.giveMeOne()
            println(rawPassword)
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

            When("로그인 요청을 한다.") {
                val actual =
                    authenticateSignInService.signIn(
                        authenticate,
                        rawPassword,
                    )

                Then("성공 플래그 true, 로그인 히스토리 1개 생성") {

                    actual.isSuccess shouldBe true
                    actual.accessHistories() shouldHaveSize 1
                    actual.accessHistories()[0].accessDetails.accessStatus shouldBe
                        AccessStatus.SUCCESS
                }
            }
        }
    },
)
