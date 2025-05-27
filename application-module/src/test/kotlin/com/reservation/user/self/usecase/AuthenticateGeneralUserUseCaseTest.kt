package com.reservation.user.self.usecase

import com.navercorp.fixturemonkey.api.jqwik.JavaTypeArbitraryGenerator
import com.navercorp.fixturemonkey.api.jqwik.JqwikPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.authenticate.service.AuthenticateSignInService
import com.reservation.enumeration.JWTVersion.V1
import com.reservation.enumeration.UserStatus
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.exceptions.WrongLoginIdOrPasswordException
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesCommand
import com.reservation.user.self.port.input.AuthenticateGeneralUserQuery.GeneralUserQueryDto
import com.reservation.user.self.port.output.AuthenticateGeneralUser
import com.reservation.user.self.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserResult
import com.reservation.user.self.port.output.UpdateAuthenticateResult
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import com.reservation.utilities.provider.JWTProvider
import com.reservation.utilities.provider.JWTRecord
import com.reservation.utilities.provider.TokenProvider
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import net.jqwik.api.Arbitraries
import net.jqwik.api.arbitraries.StringArbitrary
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AuthenticateGeneralUserUseCaseTest {
    @SpyK
    private var authenticateSignInService = AuthenticateSignInService()

    @MockK
    private lateinit var authenticateGeneralUser: AuthenticateGeneralUser

    @MockK
    private lateinit var createUserHistoriesCommand: CreateUserAccessHistoriesCommand

    @MockK
    private lateinit var updateAuthenticateResult: UpdateAuthenticateResult

    @MockK
    private var tokenProvider: TokenProvider<JWTRecord> =
        JWTProvider(
            duration = 300000L,
            secret = "af869782f3abde439def4230f2b05bd701f1ab047ec80808d3b84f1c8923506a",
            issuer = "reservation",
            version = V1,
        )

    @InjectMockKs
    private lateinit var useCase: AuthenticateGeneralUserUseCase

    @DisplayName("[실패]: 잘못된 비밀번호로 로그인 시도")
    @Test
    fun `password is wrong`() {
        val fixtureMonkey =
            FixtureMonkeyFactory.giveMePureMonkey()
                .plugin {
                    JqwikPlugin().javaTypeArbitraryGenerator(
                        object : JavaTypeArbitraryGenerator {
                            override fun strings(): StringArbitrary {
                                return Arbitraries.strings()
                                    .alpha()
                                    .ofMaxLength(18)
                                    .ofMinLength(8)
                            }
                        },
                    )
                }
                .build()

        // given
        val rawPassword: String = fixtureMonkey.giveMeOne()
        val encryptedPassword = PasswordEncoderUtility.encode("TEST")

        val queryResult =
            fixtureMonkey.giveMeBuilder<AuthenticateGeneralUserResult>()
                .set("password", encryptedPassword)
                .set("failCount", 0)
                .set("userStatus", UserStatus.ACTIVATED)
                .setNull("lockedDatetime")
                .sample()

        val query =
            fixtureMonkey.giveMeBuilder<GeneralUserQueryDto>()
                .set("password", rawPassword)
                .sample()

        // when
        every { authenticateGeneralUser.query(any()) } returns queryResult
        every { authenticateSignInService.signIn(any(), any()) } answers { callOriginal() }
        every {
            createUserHistoriesCommand.execute(any())
            updateAuthenticateResult.save(any())
        } returns Unit

        assertThatThrownBy {
            useCase.execute(query)
        }
            .isInstanceOf(WrongLoginIdOrPasswordException::class.java)
    }
}
