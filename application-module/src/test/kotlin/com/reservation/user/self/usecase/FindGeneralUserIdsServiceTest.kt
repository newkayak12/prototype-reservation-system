package com.reservation.user.self.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.user.self.port.input.FindGeneralUserIdsQuery.FindGeneralUserIdQueryDto
import com.reservation.user.self.port.output.FindGeneralUserIds
import com.reservation.user.self.port.output.FindGeneralUserIds.FindGeneralUserIdResult
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import net.jqwik.api.Arbitraries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class FindGeneralUserIdsServiceTest {
    @MockK
    private lateinit var findGeneralUserIds: FindGeneralUserIds

    @InjectMockKs
    private lateinit var useCase: FindGeneralUserIdsService

    @DisplayName("찾고자 하는 아이디가 없다.")
    @Test
    fun `empty result`() {
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val query = pureMonkey.giveMeOne<FindGeneralUserIdQueryDto>()

        every { findGeneralUserIds.query(any()) } returns emptyList()

        val actual = useCase.execute(query)

        assertThat(actual).isEmpty()
    }

    @DisplayName("찾고자 하는 아이디가 5개 있다.")
    @Test
    fun `5 id exists`() {
        val pureMonkey =
            FixtureMonkeyFactory.giveMePureMonkey()
                .build()
        val query = pureMonkey.giveMeOne<FindGeneralUserIdQueryDto>()

        every {
            findGeneralUserIds.query(any())
        } returns
            pureMonkey.giveMeBuilder<FindGeneralUserIdResult>()
                .set("userId", Arbitraries.strings().ofMinLength(8))
                .sampleList(5)

        val actual = useCase.execute(query)

        assertThat(actual).hasSize(5)
        assertThat(actual).map<String> {
            it.userId
        }
            .allSatisfy {
                it.contains("*")
            }
    }
}
