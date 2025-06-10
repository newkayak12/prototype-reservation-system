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
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.DisplayName
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
}
