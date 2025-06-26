package com.reservation.user.history.change.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.history.change.port.input.CreateGeneralUserChangeHistoryCommand.CreateGeneralUserChangeHistoryCommandDto
import com.reservation.user.history.change.port.output.CreateGeneralUserChangeHistory
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class CreateGeneralUserChangeHistoryUseCaseTest {
    @MockK
    private lateinit var createGeneralUserChangeHistory: CreateGeneralUserChangeHistory

    @InjectMockKs
    private lateinit var useCase: CreateGeneralUserChangeHistoryUseCase

    @Test
    @DisplayName("[성공]: 사용자에 변경이 생겨 변경점을 저장한다.")
    fun `save change history at once`() {
        // given
        val fixtureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val request = fixtureMonkey.giveMeOne<CreateGeneralUserChangeHistoryCommandDto>()

        every { createGeneralUserChangeHistory.save(any()) } just Runs

        // when
        assertDoesNotThrow { useCase.execute(request) }

        // then
        verify(exactly = 1) { createGeneralUserChangeHistory.save(any()) }
    }
}
