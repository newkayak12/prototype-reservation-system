package com.reservation.user.history.access.usecase

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.history.access.port.input.command.request.CreateUserHistoryCommand
import com.reservation.user.history.access.port.output.CreateUserAccessHistories
import com.reservation.user.history.access.port.output.CreateUserAccessHistories.CreateUserHistoryInquiry
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class CreateUserAccessHistoriesServiceTest {
    @MockK
    lateinit var createUserAccessHistories: CreateUserAccessHistories

    @InjectMockKs
    lateinit var createUserAccessHistoriesService: CreateUserAccessHistoriesService

    @Test
    @DisplayName("[성공]: 사용자 접근에 따라 액세스 기록들을 저장한다.")
    fun `save access histories at once`() {
        // given
        val fixtureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val target: List<CreateUserHistoryCommand> = fixtureMonkey.giveMe(10)

        // when
        every { createUserAccessHistories.saveAll(any()) } returns Unit

        // then
        assertDoesNotThrow {
            createUserAccessHistoriesService.execute(target)
        }
        verify(exactly = 1) {
            createUserAccessHistories.saveAll(any<List<CreateUserHistoryInquiry>>())
        }
    }

    @Test
    @DisplayName("[성공]: 사용자 접근 기록이 없어 아무런 작업을 하지 않는다.")
    fun `save access histories not work`() {
        // given
        val target: List<CreateUserHistoryCommand> = emptyList()

        // when

        // then
        assertDoesNotThrow {
            createUserAccessHistoriesService.execute(target)
        }
        verify(exactly = 0) {
            createUserAccessHistories.saveAll(any<List<CreateUserHistoryInquiry>>())
        }
    }
}
