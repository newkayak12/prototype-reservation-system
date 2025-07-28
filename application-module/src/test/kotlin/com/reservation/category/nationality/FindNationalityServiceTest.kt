package com.reservation.category.nationality

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.category.nationality.port.input.FindNationalitiesQuery.FindNationalitiesQueryDto
import com.reservation.category.nationality.port.output.FindNationalities
import com.reservation.category.nationality.port.output.FindNationalities.FindNationalitiesResult
import com.reservation.category.nationality.usecase.FindNationalitiesService
import com.reservation.fixture.FixtureMonkeyFactory
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import net.jqwik.api.Arbitraries
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class FindNationalityServiceTest {
    @MockK
    private lateinit var findNationalities: FindNationalities

    @InjectMockKs
    private lateinit var useCase: FindNationalitiesService

    @DisplayName("카테고리 조회 요청을 진행하고 18건의 결과가 조회된다.")
    @Test
    fun findNationalities() {
        val size = 18
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val request = pureMonkey.giveMeOne<FindNationalitiesQueryDto>()
        val resultList = pureMonkey.giveMe<FindNationalitiesResult>(size)

        every {
            findNationalities.query(any())
        } returns resultList

        val result = useCase.execute(request)

        assertThat(result).hasSize(size)

        verify(exactly = 1) {
            findNationalities.query(any())
        }
    }

    @DisplayName("title을 입력해서 카테고리 조회 요청을 진행하고 10건의 결과가 조회된다.")
    @Test
    fun findNationalitiesByTitle() {
        val size = 10
        val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
        val request =
            pureMonkey.giveMeBuilder<FindNationalitiesQueryDto>()
                .set("title", Arbitraries.strings().sample())
                .sample()
        val resultList = pureMonkey.giveMe<FindNationalitiesResult>(size)

        every {
            findNationalities.query(any())
        } returns resultList

        val result = useCase.execute(request)

        assertThat(result).hasSize(size)

        verify(exactly = 1) {
            findNationalities.query(any())
        }
    }
}
