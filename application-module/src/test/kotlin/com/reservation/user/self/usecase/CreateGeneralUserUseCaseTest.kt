package com.reservation.user.self.usecase

import com.reservation.common.exceptions.AlreadyPersistedException
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.user.self.port.input.CreateGeneralUserCommand.CreateGeneralUserCommandDto
import com.reservation.user.self.port.output.CheckGeneralUserDuplicated
import com.reservation.user.self.port.output.CheckGeneralUserDuplicated.CheckGeneralUserDuplicatedInquiry
import com.reservation.user.self.port.output.CreateGeneralUser
import com.reservation.user.self.port.output.CreateGeneralUser.CreateGeneralUserInquiry
import com.reservation.user.self.service.CreateGeneralUserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class CreateGeneralUserUseCaseTest {
    @MockK
    private lateinit var createGeneralUser: CreateGeneralUser

    @MockK
    private lateinit var checkGeneralUserDuplicated: CheckGeneralUserDuplicated

    @SpyK
    private var createGeneralUserService = CreateGeneralUserService()

    @InjectMockKs
    private lateinit var useCase: CreateGeneralUserUseCase

    @Test
    @DisplayName("이미 존재하는 아이디로 중복체크를 통과하지 못 함.")
    fun `it's duplicated login id `() {
        val command =
            CreateGeneralUserCommandDto(
                loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
            )

        every {
            checkGeneralUserDuplicated.isDuplicated(any<CheckGeneralUserDuplicatedInquiry>())
        } returns true

        assertThrows<AlreadyPersistedException> {
            useCase.execute(command)
        }
    }

    @Test
    @DisplayName("여러 제약 조건을 만족하여 계정이 생성된다.")
    fun `create a user successfully`() {
        val command =
            CreateGeneralUserCommandDto(
                loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
            )
        val expected = true

        every {
            checkGeneralUserDuplicated.isDuplicated(any<CheckGeneralUserDuplicatedInquiry>())
        } returns false

        every {
            createGeneralUser.save(any<CreateGeneralUserInquiry>())
        } returns true

        val result = useCase.execute(command)

        assertEquals(expected, result)
    }
}
