package com.reservation.user.self.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.exceptions.InvalidSituationException
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.user.exceptions.NoSuchDatabaseElementException
import com.reservation.user.self.User
import com.reservation.user.self.port.input.FindGeneralUserPasswordCommand.FindGeneralUserPasswordCommandDto
import com.reservation.user.self.port.output.LoadGeneralUserByLoginIdAndEmail
import com.reservation.user.self.port.output.SendFindGeneralUserPasswordAsEmail
import com.reservation.user.self.port.output.UpdateGeneralUserTemporaryPassword
import com.reservation.user.service.ChangeGeneralUserPasswordService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class FindGeneralUserPasswordUseCaseTest {
    @MockK
    private lateinit var changeGeneralUserPasswordService: ChangeGeneralUserPasswordService

    @MockK
    private lateinit var loadGeneralUserByLoginIdAndEmail: LoadGeneralUserByLoginIdAndEmail

    @MockK
    private lateinit var updateGeneralUserTemporaryPassword: UpdateGeneralUserTemporaryPassword

    @MockK
    private lateinit var sendFindGeneralUserPasswordAsEmail: SendFindGeneralUserPasswordAsEmail

    @InjectMockKs
    private lateinit var useCase: FindGeneralUserPasswordUseCase

    @DisplayName("[실패]")
    @Nested
    inner class Failure {
        @DisplayName(value = "찾고자 하는 계정이 없는 경우")
        @Test
        fun `no such user`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            // given
            val command = pureMonkey.giveMeOne<FindGeneralUserPasswordCommandDto>()

            every {
                loadGeneralUserByLoginIdAndEmail.load(any())
            } returns null

            assertThrows<NoSuchDatabaseElementException> {
                useCase.execute(command)
            }
        }

        @DisplayName(value = "있어야 할 요소(id)가 없는 경우")
        @Test
        fun `invalid id state`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<FindGeneralUserPasswordCommandDto>()
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
                loadGeneralUserByLoginIdAndEmail.load(any())
            } returns pureMonkey.giveMeOne()

            every {
                changeGeneralUserPasswordService.changePassword(any(), any(), eq(true))
            } returns changePasswordResult

            assertThrows<InvalidSituationException> {
                useCase.execute(command)
            }
        }

        @DisplayName(value = "있어야 할 요소(userOldEncodedPassword)가 없는 경우")
        @Test
        fun `old password is not exists`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<FindGeneralUserPasswordCommandDto>()
            val changePasswordResult =
                User(
                    id = UuidGenerator.generate(),
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
                loadGeneralUserByLoginIdAndEmail.load(any())
            } returns pureMonkey.giveMeOne()

            every {
                changeGeneralUserPasswordService.changePassword(any(), any(), eq(true))
            } returns changePasswordResult

            assertThrows<InvalidSituationException> {
                useCase.execute(command)
            }
        }

        @DisplayName(value = "있어야 할 요소(userPasswordChangedDatetime)가 없는 경우")
        @Test
        fun `userPasswordChangedDatetime is not exists`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<FindGeneralUserPasswordCommandDto>()
            val changePasswordResult =
                User(
                    id = UuidGenerator.generate(),
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
                loadGeneralUserByLoginIdAndEmail.load(any())
            } returns pureMonkey.giveMeOne()

            every {
                changeGeneralUserPasswordService.changePassword(any(), any(), eq(true))
            } returns changePasswordResult

            assertThrows<InvalidSituationException> {
                useCase.execute(command)
            }
        }

        @DisplayName(value = "패스워드 업데이트가 실패한 경우")
        @Test
        fun `password update is failed`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<FindGeneralUserPasswordCommandDto>()
            val expected = false
            val changePasswordResult =
                User(
                    id = UuidGenerator.generate(),
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
                loadGeneralUserByLoginIdAndEmail.load(any())
            } returns pureMonkey.giveMeOne()

            every {
                changeGeneralUserPasswordService.changePassword(any(), any(), eq(true))
            } returns changePasswordResult

            every {
                updateGeneralUserTemporaryPassword.command(
                    any(),
                )
            } returns false

            assertEquals(expected, useCase.execute(command))
        }
    }

    @DisplayName("[성공]")
    @Nested
    inner class Success {
        @DisplayName(value = "아이디, 이메일로 비밀번호 재발급 후 이메일 발송")
        @Test
        fun `password update is failed`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val command = pureMonkey.giveMeOne<FindGeneralUserPasswordCommandDto>()
            val expected = true
            val changePasswordResult =
                User(
                    id = UuidGenerator.generate(),
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
                loadGeneralUserByLoginIdAndEmail.load(any())
            } returns pureMonkey.giveMeOne()

            every {
                changeGeneralUserPasswordService.changePassword(any(), any(), eq(true))
            } returns changePasswordResult

            every {
                updateGeneralUserTemporaryPassword.command(
                    any(),
                )
            } returns true

            every {
                sendFindGeneralUserPasswordAsEmail.execute(any())
            } just Runs

            assertEquals(expected, useCase.execute(command))
        }
    }
}
