package com.reservation.authenticate.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.authenticate.port.input.query.request.SellerUserQuery
import com.reservation.authenticate.port.output.AuthenticateSellerUser
import com.reservation.authenticate.port.output.AuthenticateSellerUser.AuthenticateSellerUserResult
import com.reservation.authenticate.port.output.SaveSellerUserRefreshToken
import com.reservation.authenticate.port.output.UpdateAuthenticateResult
import com.reservation.authenticate.service.AuthenticateSignInDomainService
import com.reservation.common.exceptions.AccessFailureCountHasExceedException
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.common.exceptions.WrongLoginIdOrPasswordException
import com.reservation.enumeration.JWTVersion.V1
import com.reservation.enumeration.UserStatus
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.history.access.port.input.CreateUserAccessHistoriesUseCase
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import com.reservation.utilities.provider.JWTProvider
import com.reservation.utilities.provider.JWTRecord
import com.reservation.utilities.provider.TokenProvider
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class AuthenticateSellerUserServiceTest {
    @MockK
    private lateinit var authenticateSignInDomainService: AuthenticateSignInDomainService

    @MockK
    private lateinit var authenticateSellerUser: AuthenticateSellerUser

    @MockK
    private lateinit var createSellerUserAccessHistoriesCommand: CreateUserAccessHistoriesUseCase

    @MockK
    private lateinit var updateSellerUserAuthenticateResult: UpdateAuthenticateResult

    @SpyK
    private var tokenProvider: TokenProvider<JWTRecord> =
        JWTProvider(
            duration = 300000L,
            secret = "af869782f3abde439def4230f2b05bd701f1ab047ec80808d3b84f1c8923506a",
            issuer = "reservation",
            version = V1,
        )

    @MockK
    private lateinit var saveSellerUserRefreshToken: SaveSellerUserRefreshToken

    @InjectMockKs
    private lateinit var useCase: AuthenticateSellerUserService

    @DisplayName("[실패]")
    @Nested
    inner class Failure {
        @DisplayName("잘못된 아이디로 로그인 시도를 한다.")
        @Test
        fun `try sign-in with wrong loginId`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request = pureMonkey.giveMeOne<SellerUserQuery>()

            every {
                authenticateSellerUser.query(any())
            } returns null

            assertThrows<NoSuchPersistedElementException> {
                useCase.execute(request)
            }

            verify(exactly = 1) {
                authenticateSellerUser.query(any())
            }
        }

        @DisplayName("잘못된 비밀번호로 로그인 시도를 한다.")
        @Test
        fun `try sign-in with wrong password`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request =
                pureMonkey.giveMeBuilder<SellerUserQuery>()
                    .set("loginId", CommonlyUsedArbitraries.loginIdArbitrary.sample())
                    .sample()
            val rawPassword = CommonlyUsedArbitraries.passwordArbitrary.sample()
            val encryptedPassword = PasswordEncoderUtility.encode(rawPassword + "1")
            val authenticateSellerUserResult =
                pureMonkey.giveMeBuilder<AuthenticateSellerUserResult>()
                    .set("password", encryptedPassword)
                    .set(
                        "failCount",
                        Arbitraries.integers().lessOrEqual(3).greaterOrEqual(0).sample(),
                    )
                    .setNull("lockedDatetime")
                    .set("userStatus", UserStatus.ACTIVATED)
                    .sample()
            every {
                authenticateSellerUser.query(any())
            } returns authenticateSellerUserResult

            every {
                authenticateSignInDomainService.signIn(any(), any())
            } answers { callOriginal() }

            every {
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
            } just runs

            assertThrows<WrongLoginIdOrPasswordException> {
                useCase.execute(request)
            }

            verify(exactly = 1) {
                authenticateSellerUser.query(any())
                authenticateSignInDomainService.signIn(any(), any())
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
            }
        }

        @DisplayName("반복되는 로그인 실패로 정지 상태에 돌입한다.")
        @Test
        fun `try sign-in with wrong loginId or password and blocked`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val request =
                pureMonkey.giveMeBuilder<SellerUserQuery>()
                    .set("loginId", CommonlyUsedArbitraries.loginIdArbitrary.sample())
                    .sample()
            val rawPassword = CommonlyUsedArbitraries.passwordArbitrary.sample()
            val encryptedPassword = PasswordEncoderUtility.encode(rawPassword + "1")
            val authenticateSellerUserResult =
                pureMonkey.giveMeBuilder<AuthenticateSellerUserResult>()
                    .set("password", encryptedPassword)
                    .set("failCount", 4)
                    .set("userStatus", UserStatus.ACTIVATED)
                    .sample()

            every {
                authenticateSellerUser.query(any())
            } returns authenticateSellerUserResult

            every {
                authenticateSignInDomainService.signIn(any(), any())
            } answers { callOriginal() }

            every {
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
            } just runs

            assertThrows<AccessFailureCountHasExceedException> {
                useCase.execute(request)
            }

            verify(exactly = 1) {
                authenticateSellerUser.query(any())
                authenticateSignInDomainService.signIn(any(), any())
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
            }
        }

        @DisplayName("계정 정지인 아이디에 로그인을 한다.")
        @Test
        fun `try sign-in  blocked account`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val request =
                pureMonkey.giveMeBuilder<SellerUserQuery>()
                    .set("loginId", CommonlyUsedArbitraries.loginIdArbitrary.sample())
                    .sample()
            val rawPassword = CommonlyUsedArbitraries.passwordArbitrary.sample()
            val encryptedPassword = PasswordEncoderUtility.encode(rawPassword + "1")
            val authenticateSellerUserResult =
                pureMonkey.giveMeBuilder<AuthenticateSellerUserResult>()
                    .set("password", encryptedPassword)
                    .set("failCount", 5)
                    .set("userStatus", UserStatus.DEACTIVATED)
                    .set("lockedDatetime", LocalDateTime.now().plusMinutes(1L))
                    .sample()

            every {
                authenticateSellerUser.query(any())
            } returns authenticateSellerUserResult

            every {
                authenticateSignInDomainService.signIn(any(), any())
            } answers { callOriginal() }

            every {
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
            } just runs

            assertThrows<AccessFailureCountHasExceedException> {
                useCase.execute(request)
            }

            verify(exactly = 1) {
                authenticateSellerUser.query(any())
                authenticateSignInDomainService.signIn(any(), any())
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
            }
        }
    }

    @DisplayName("[성공]")
    @Nested
    inner class Success {
        @DisplayName("계정 정지이고 정지 시간 이후의 계정에 로그인을 한다.")
        @Test
        fun `try sign-in blocked account but blocking time expired`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val rawPassword = CommonlyUsedArbitraries.passwordArbitrary.sample()
            val request =
                pureMonkey.giveMeBuilder<SellerUserQuery>()
                    .set("loginId", CommonlyUsedArbitraries.loginIdArbitrary.sample())
                    .set("password", rawPassword)
                    .sample()

            val encryptedPassword = PasswordEncoderUtility.encode(rawPassword)
            val authenticateSellerUserResult =
                pureMonkey.giveMeBuilder<AuthenticateSellerUserResult>()
                    .set("password", encryptedPassword)
                    .set("failCount", 5)
                    .set("userStatus", UserStatus.DEACTIVATED)
                    .set("lockedDatetime", LocalDateTime.now().minusDays(1L))
                    .sample()

            every {
                authenticateSellerUser.query(any())
            } returns authenticateSellerUserResult

            every {
                authenticateSignInDomainService.signIn(any(), eq(rawPassword))
            } answers { callOriginal() }

            every {
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
                saveSellerUserRefreshToken.command(any())
            } just runs

            val result = useCase.execute(request)
            assertThat(result).isNotNull
            assertThat(result)
                .extracting("accessToken", "refreshToken")
                .allSatisfy {
                    assertThat(it).isNotNull
                    assertThat(it).isInstanceOf(String::class.java)
                    assertThat(it).asString().contains("Bearer ")
                }

            verify(exactly = 1) {
                authenticateSellerUser.query(any())
                authenticateSignInDomainService.signIn(any(), any())
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
                saveSellerUserRefreshToken.command(any())
            }
            verify(exactly = 2) {
                tokenProvider.tokenize(any(), any())
            }
        }

        @DisplayName("정상적인 상태의 계정에 올바른 아이디, 올바른 비밀번호로 로그인을 한다.")
        @Test
        fun `try sign-in then success`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val rawPassword = CommonlyUsedArbitraries.passwordArbitrary.sample()
            val request =
                pureMonkey.giveMeBuilder<SellerUserQuery>()
                    .set("loginId", CommonlyUsedArbitraries.loginIdArbitrary.sample())
                    .set("password", rawPassword)
                    .sample()

            val encryptedPassword = PasswordEncoderUtility.encode(rawPassword)
            val authenticateSellerUserResult =
                pureMonkey.giveMeBuilder<AuthenticateSellerUserResult>()
                    .set("password", encryptedPassword)
                    .set(
                        "failCount",
                        Arbitraries.integers().lessOrEqual(4).greaterOrEqual(0).sample(),
                    )
                    .set("userStatus", UserStatus.ACTIVATED)
                    .setNull("lockedDatetime")
                    .sample()

            every {
                authenticateSellerUser.query(any())
            } returns authenticateSellerUserResult

            every {
                authenticateSignInDomainService.signIn(any(), eq(rawPassword))
            } answers { callOriginal() }

            every {
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
                saveSellerUserRefreshToken.command(any())
            } just runs

            val result = useCase.execute(request)
            assertThat(result).isNotNull
            assertThat(result)
                .extracting("accessToken", "refreshToken")
                .allSatisfy {
                    assertThat(it).isNotNull
                    assertThat(it).isInstanceOf(String::class.java)
                    assertThat(it).asString().contains("Bearer ")
                }

            verify(exactly = 1) {
                authenticateSellerUser.query(any())
                authenticateSignInDomainService.signIn(any(), any())
                createSellerUserAccessHistoriesCommand.execute(any())
                updateSellerUserAuthenticateResult.command(any())
                saveSellerUserRefreshToken.command(any())
            }
            verify(exactly = 2) {
                tokenProvider.tokenize(any(), any())
            }
        }
    }
}
