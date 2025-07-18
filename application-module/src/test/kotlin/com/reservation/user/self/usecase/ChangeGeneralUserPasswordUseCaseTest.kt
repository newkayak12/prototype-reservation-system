package com.reservation.user.self.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.exceptions.InvalidSituationException
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.self.User
import com.reservation.user.self.port.input.ChangeGeneralUserPasswordCommand.ChangeGeneralUserPasswordCommandDto
import com.reservation.user.self.port.output.ChangeGeneralUserPassword
import com.reservation.user.self.port.output.LoadGeneralUser
import com.reservation.user.self.port.output.LoadGeneralUser.LoadGeneralUserResult
import com.reservation.user.service.ChangeGeneralUserPasswordService
import com.reservation.user.shared.vo.LoginId
import com.reservation.user.shared.vo.Password
import com.reservation.user.shared.vo.PersonalAttributes
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class ChangeGeneralUserPasswordUseCaseTest {
    @MockK
    private lateinit var changeGeneralUserPassword: ChangeGeneralUserPassword

    @MockK
    private lateinit var loadGeneralUser: LoadGeneralUser

    @SpyK
    private var changeGeneralUserPasswordService = ChangeGeneralUserPasswordService()

    @InjectMockKs
    private lateinit var useCase: ChangeGeneralUserPasswordUseCase

    @Nested
    @DisplayName("[실패]")
    inner class Failure {
        @Test
        @DisplayName("DB에서 조회된 데이터가 없어서 exception을 던진다.")
        fun `there is no data`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<ChangeGeneralUserPasswordCommandDto>()

            every {
                loadGeneralUser.load(any())
            } returns null

            assertThrows<NoSuchPersistedElementException> {
                useCase.execute(command)
            }
        }

        @Test
        @DisplayName("비밀번호 변경 중 필요한 값(ID)가 없다.")
        fun `invalid situation_nullish id`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<ChangeGeneralUserPasswordCommandDto>()
            val loadResult = pureMonkey.giveMeOne<LoadGeneralUserResult>()
            val changePasswordResult =
                User(
                    loginId =
                        LoginId(
                            loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                        ),
                    password =
                        Password(
                            encodedPassword = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                            oldEncodedPassword = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                            changedDateTime = LocalDateTime.now(),
                        ),
                    personalAttributes =
                        PersonalAttributes(
                            email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                            mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                        ),
                    nickname = pureMonkey.giveMeOne(),
                )

            every {
                loadGeneralUser.load(any())
            } returns loadResult

            every {
                changeGeneralUserPasswordService.changePassword(any(), any())
            } returns changePasswordResult

            assertThrows<InvalidSituationException> {
                useCase.execute(command)
            }
        }

        @Test
        @DisplayName("비밀번호 변경 중 필요한 값(old password)가 없다.")
        fun `invalid situation_nullish old password`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<ChangeGeneralUserPasswordCommandDto>()
            val loadResult = pureMonkey.giveMeOne<LoadGeneralUserResult>()
            val changePasswordResult =
                User(
                    id = pureMonkey.giveMeOne(),
                    loginId =
                        LoginId(
                            loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                        ),
                    password =
                        Password(
                            encodedPassword = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                            oldEncodedPassword = null,
                            changedDateTime = LocalDateTime.now(),
                        ),
                    personalAttributes =
                        PersonalAttributes(
                            email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                            mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                        ),
                    nickname = pureMonkey.giveMeOne(),
                )

            every {
                loadGeneralUser.load(any())
            } returns loadResult

            every {
                changeGeneralUserPasswordService.changePassword(any(), any())
            } returns changePasswordResult

            assertThrows<InvalidSituationException> {
                useCase.execute(command)
            }
        }

        @Test
        @DisplayName("비밀번호 변경 중 필요한 값(changedPasswordDatetime)가 없다.")
        fun `invalid situation_nullish changed password datetime`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<ChangeGeneralUserPasswordCommandDto>()
            val loadResult = pureMonkey.giveMeOne<LoadGeneralUserResult>()
            val changePasswordResult =
                User(
                    id = pureMonkey.giveMeOne(),
                    loginId =
                        LoginId(
                            loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                        ),
                    password =
                        Password(
                            encodedPassword = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                            oldEncodedPassword = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                            changedDateTime = null,
                        ),
                    personalAttributes =
                        PersonalAttributes(
                            email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                            mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                        ),
                    nickname = pureMonkey.giveMeOne(),
                )

            every {
                loadGeneralUser.load(any())
            } returns loadResult

            every {
                changeGeneralUserPasswordService.changePassword(any(), any())
            } returns changePasswordResult

            assertThrows<InvalidSituationException> {
                useCase.execute(command)
            }
        }
    }

    @Nested
    @DisplayName("[성공]")
    inner class Success {
        @Test
        @DisplayName("모든 값이 있고 성공적으로 비밀번호가 변경된다.")
        fun `change password`() {
            val expected = true
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<ChangeGeneralUserPasswordCommandDto>()
            val loadResult = pureMonkey.giveMeOne<LoadGeneralUserResult>()
            val changePasswordResult =
                User(
                    id = pureMonkey.giveMeOne(),
                    loginId =
                        LoginId(
                            loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                        ),
                    password =
                        Password(
                            encodedPassword = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                            oldEncodedPassword = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                            changedDateTime = LocalDateTime.now(),
                        ),
                    personalAttributes =
                        PersonalAttributes(
                            email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                            mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                        ),
                    nickname = pureMonkey.giveMeOne(),
                )

            every {
                loadGeneralUser.load(any())
            } returns loadResult

            every {
                changeGeneralUserPasswordService.changePassword(any(), any())
            } returns changePasswordResult

            every {
                changeGeneralUserPassword.command(any())
            } returns true

            val actual = useCase.execute(command)

            assertEquals(expected, actual)
        }
    }
}
