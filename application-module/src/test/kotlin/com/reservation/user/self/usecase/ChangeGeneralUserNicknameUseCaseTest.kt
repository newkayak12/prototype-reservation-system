package com.reservation.user.self.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.common.exceptions.AlreadyPersistedException
import com.reservation.exceptions.InvalidSituationException
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.user.exceptions.NoSuchDatabaseElementException
import com.reservation.user.history.change.port.input.CreateGeneralUserChangeHistoryCommand
import com.reservation.user.self.User
import com.reservation.user.self.port.input.ChangeGeneralUserNicknameCommand.ChangeGeneralUserNicknameCommandDto
import com.reservation.user.self.port.output.ChangeGeneralUserNickname
import com.reservation.user.self.port.output.CheckGeneralUserNicknameDuplicated
import com.reservation.user.self.port.output.LoadGeneralUser
import com.reservation.user.self.port.output.LoadGeneralUser.LoadGeneralUserResult
import com.reservation.user.service.ChangeUserNicknameService
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import net.jqwik.api.Arbitraries
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.time.LocalDateTime

@ExtendWith(MockKExtension::class)
class ChangeGeneralUserNicknameUseCaseTest {
    @MockK
    private lateinit var changeGeneralUserNickname: ChangeGeneralUserNickname

    @MockK
    private lateinit var checkGeneralUserNicknameDuplicated: CheckGeneralUserNicknameDuplicated

    @MockK
    private lateinit var loadGeneralUser: LoadGeneralUser

    @MockK
    private lateinit var createGeneralUserChangeHistoryCommand:
        CreateGeneralUserChangeHistoryCommand

    @SpyK
    private var changeUserNicknameService = ChangeUserNicknameService()

    @InjectMockKs
    private lateinit var useCase: ChangeGeneralUserNicknameUseCase

    @DisplayName("[실패]")
    @Nested
    inner class Failure {
        @DisplayName("사용 중인 닉네임이어서 에러 메시지를 출력한다.")
        @Test
        fun `it s duplicated nickname`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val command = pureMonkey.giveMeOne<ChangeGeneralUserNicknameCommandDto>()

            every {
                checkGeneralUserNicknameDuplicated.isDuplicated(any())
            } returns true

            assertThrows<AlreadyPersistedException> {
                useCase.execute(command)
            }
        }

        @DisplayName("중복 체크는 통과했지만, 해당 사용자를 찾을 수 없다.")
        @Test
        fun `can not find use`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val command = pureMonkey.giveMeOne<ChangeGeneralUserNicknameCommandDto>()

            every {
                checkGeneralUserNicknameDuplicated.isDuplicated(any())
            } returns false

            every {
                loadGeneralUser.load(any())
            } returns null

            assertThrows<NoSuchDatabaseElementException> {
                useCase.execute(command)
            }
        }

        @DisplayName("중복 체크는 통과했고 사용자가 있지만 일부 필드가 올바르지 않다.")
        @Test
        fun `user is existed but some field has invalid state`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val command = pureMonkey.giveMeOne<ChangeGeneralUserNicknameCommandDto>()
            val loadResult = pureMonkey.giveMeOne<LoadGeneralUserResult>()
            val invalidUser =
                User(
                    id = null,
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
                checkGeneralUserNicknameDuplicated.isDuplicated(any())
            } returns false

            every {
                loadGeneralUser.load(any())
            } returns loadResult

            every {
                createGeneralUserChangeHistoryCommand.execute(any())
            } just Runs

            every {
                changeUserNicknameService.changePersonalAttributes(any(), any())
            } returns invalidUser

            assertThrows<InvalidSituationException> {
                useCase.execute(command)
            }
        }
    }

    @DisplayName("[성공]")
    @Nested
    inner class Success {
        @DisplayName("모든 조건을 통과하고 닉네임 변경에 성공한다.")
        @Test
        fun successChangeNickname() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val command = pureMonkey.giveMeOne<ChangeGeneralUserNicknameCommandDto>()
            val loadResult = pureMonkey.giveMeOne<LoadGeneralUserResult>()
            val validUser =
                User(
                    id = Arbitraries.strings().sample(),
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
            val expected = true

            every {
                checkGeneralUserNicknameDuplicated.isDuplicated(any())
            } returns false

            every {
                loadGeneralUser.load(any())
            } returns loadResult

            every {
                createGeneralUserChangeHistoryCommand.execute(any())
            } just Runs

            every {
                changeUserNicknameService.changePersonalAttributes(any(), any())
            } returns validUser

            every {
                changeGeneralUserNickname.changeGeneralUserNickname(any())
            } returns true

            assertEquals(expected, useCase.execute(command))
        }
    }
}
