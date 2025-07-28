package com.reservation.category.cuisine

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.category.cuisine.port.input.FindCuisinesQuery.FindCuisinesQueryDto
import com.reservation.category.cuisine.port.output.FindCuisines
import com.reservation.category.cuisine.port.output.FindCuisines.FindCuisinesResult
import com.reservation.category.cuisine.usecase.FindCuisinesService
import com.reservation.fixture.FixtureMonkeyFactory
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class FindCuisinesServiceTest {
    @MockK
    private lateinit var findCuisines: FindCuisines

    @InjectMockKs
    private lateinit var useCase: FindCuisinesService

    @DisplayName("음식 종류 카테고리를 조회하고 총 16가 조회된다.")
    @Test
    fun `find cuisine category`() {
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val size = 16
        val queryResult = pureMonkey.giveMe<FindCuisinesResult>(size)
        val request = pureMonkey.giveMeOne<FindCuisinesQueryDto>()

        every {
            findCuisines.query(any())
        } returns queryResult

        val result = useCase.execute(request)

        assertThat(result).hasSize(size)
        verify(exactly = 1) {
            findCuisines.query(any())
        }
    }

    @DisplayName("16가지 음식 종류 카테고리가 존재하고 title로 검색해서 총 2개가 조회된다.")
    @Test
    fun `find cuisine category by title`() {
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val size = 2
        val queryResult = pureMonkey.giveMe<FindCuisinesResult>(size)
        val request =
            pureMonkey.giveMeBuilder<FindCuisinesQueryDto>()
                .set("title", "test")
                .sample()

        every {
            findCuisines.query(any())
        } returns queryResult

        val result = useCase.execute(request)

        assertThat(result).hasSize(size)
        verify(exactly = 1) {
            findCuisines.query(any())
        }
    }
}
