package com.reservation.user.self.usecase

import com.reservation.user.self.port.output.CreateGeneralUser
import com.reservation.user.self.service.CreateGeneralUserService
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class CreateGeneralUserUseCaseTest {
    @MockK
    private lateinit var createGeneralUser: CreateGeneralUser

    @SpyK
    private val createGeneralUserService = CreateGeneralUserService()

    @InjectMockKs
    private lateinit var useCase: CreateGeneralUserUseCase
}
