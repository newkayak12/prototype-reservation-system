package com.reservation.category.nationality

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByIdsQuery
import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByTitleQuery
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class FindNationalityServiceTest {
    @MockK
    private lateinit var findNationalities: FindNationalities

    @InjectMockKs
    private lateinit var useCase: FindNationalitiesService

    @DisplayName("Title로 검색")
    @Nested
    inner class Title {
        @DisplayName("카테고리 조회 요청을 진행하고 18건의 결과가 조회된다.")
        @Test
        fun findNationalities() {
            val size = 18
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request = pureMonkey.giveMeOne<FindNationalitiesByTitleQuery>()
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
                pureMonkey.giveMeBuilder<FindNationalitiesByTitleQuery>()
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

    @DisplayName("Ids로 검색")
    @Nested
    inner class Ids {
        @DisplayName("카테고리 조회 요청을 진행하고 18건의 결과가 조회된다.")
        @Test
        fun findNationalities() {
            val size = 18
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request = pureMonkey.giveMeOne<FindNationalitiesByIdsQuery>()
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

        @DisplayName("Ids을 입력해서 카테고리 조회 요청을 진행하고 10건의 결과가 조회된다.")
        @Test
        fun findNationalitiesByIds() {
            val size = 10
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
            val request =
                pureMonkey.giveMeBuilder<FindNationalitiesByIdsQuery>()
                    .set("ids", pureMonkey.giveMe<Long>(10))
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
}
