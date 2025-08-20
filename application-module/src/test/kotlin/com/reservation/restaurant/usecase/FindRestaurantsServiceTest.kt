package com.reservation.restaurant.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.category.cuisine.port.input.FindCuisinesByIdsUseCase
import com.reservation.category.cuisine.port.input.query.response.FindCuisinesQueryResult
import com.reservation.category.nationality.port.input.FindNationalitiesByIdsUseCase
import com.reservation.category.nationality.port.input.query.response.FindNationalitiesQueryResult
import com.reservation.category.tag.port.input.FindTagsByIdsUseCase
import com.reservation.category.tag.port.input.query.response.FindTagsQueryResult
import com.reservation.enumeration.CategoryType
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.restaurant.port.input.query.request.FindRestaurantsQueryRequest
import com.reservation.restaurant.port.output.FindRestaurants
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsResult
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

@DisplayName("레스토랑 리스트를 조건에 맞춰 요청한다.")
@ExtendWith(MockKExtension::class)
class FindRestaurantsServiceTest {
    private val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
    private val idArbitaries = Arbitraries.integers().lessOrEqual(10).greaterOrEqual(1)

    private fun giveMeList(range: IntRange): List<FindRestaurantsResult> {
        return range.map {
            pureMonkey.giveMeBuilder<FindRestaurantsResult>()
                .set("tags", idArbitaries.sampleStream().limit(2).toList())
                .set("nationalities", idArbitaries.sampleStream().limit(2).toList())
                .set("cuisines", idArbitaries.sampleStream().limit(2).toList())
                .sample()
        }
    }

    private fun giveMeTags(range: IntRange): List<FindTagsQueryResult> {
        return range.map {
            pureMonkey.giveMeBuilder<FindTagsQueryResult>()
                .set("id", it)
                .set("categoryType", CategoryType.TAG)
                .sample()
        }
    }

    private fun giveMeNationalities(range: IntRange): List<FindNationalitiesQueryResult> {
        return range.map {
            pureMonkey.giveMeBuilder<FindNationalitiesQueryResult>()
                .set("id", it)
                .set("categoryType", CategoryType.NATIONALITY)
                .sample()
        }
    }

    private fun giveMeCuisines(range: IntRange): List<FindCuisinesQueryResult> {
        return range.map {
            pureMonkey.giveMeBuilder<FindCuisinesQueryResult>()
                .set("id", it)
                .set("categoryType", CategoryType.CUISINE)
                .sample()
        }
    }

    @DisplayName("그러나 조회된 리스트가 없고")
    @Nested
    inner class Empty {
        @MockK
        private lateinit var findRestaurants: FindRestaurants

        @MockK
        private lateinit var findTagsByIdsUseCase: FindTagsByIdsUseCase

        @MockK
        private lateinit var findNationalitiesByIdsUseCase: FindNationalitiesByIdsUseCase

        @MockK
        private lateinit var findCuisinesByIdsUseCase: FindCuisinesByIdsUseCase

        @InjectMockKs
        private lateinit var findRestaurantsService: FindRestaurantsService

        @DisplayName("결과적으로 비어있는 배열을 반환한다.")
        @Test
        fun `empty list`() {
            val query = pureMonkey.giveMeOne<FindRestaurantsQueryRequest>()
            val list = listOf<FindRestaurantsResult>()
            val tags = giveMeTags(1..10)
            val nationalities = giveMeNationalities(1..10)
            val cuisines = giveMeCuisines(1..10)

            every { findRestaurants.query(any()) } returns list
            every { findTagsByIdsUseCase.execute(any()) } returns tags
            every { findNationalitiesByIdsUseCase.execute(any()) } returns nationalities
            every { findCuisinesByIdsUseCase.execute(any()) } returns cuisines

            val result = findRestaurantsService.execute(query)

            assertThat(result).isNotNull
            assertThat(result.list).isEmpty()
            assertThat(result.hasNext).isFalse()

            verify(exactly = 1) {
                findRestaurants.query(any())
                findTagsByIdsUseCase.execute(any())
                findNationalitiesByIdsUseCase.execute(any())
                findCuisinesByIdsUseCase.execute(any())
            }
        }
    }

    @DisplayName("11건이 조회된다.")
    @Nested
    inner class NotEmpty {
        @MockK
        private lateinit var findRestaurants: FindRestaurants

        @MockK
        private lateinit var findTagsByIdsUseCase: FindTagsByIdsUseCase

        @MockK
        private lateinit var findNationalitiesByIdsUseCase: FindNationalitiesByIdsUseCase

        @MockK
        private lateinit var findCuisinesByIdsUseCase: FindCuisinesByIdsUseCase

        @InjectMockKs
        private lateinit var findRestaurantsService: FindRestaurantsService

        @DisplayName("결과적으로 10건을 확인할 수 있으며 다음 페이지가 존재한다.")
        @Test
        fun `return 10 list and has next page`() {
            val query = pureMonkey.giveMeOne<FindRestaurantsQueryRequest>()
            val list = giveMeList(1..11)
            val tags = giveMeTags(1..10)
            val nationalities = giveMeNationalities(1..10)
            val cuisines = giveMeCuisines(1..10)

            every { findRestaurants.query(any()) } returns list
            every { findTagsByIdsUseCase.execute(any()) } returns tags
            every { findNationalitiesByIdsUseCase.execute(any()) } returns nationalities
            every { findCuisinesByIdsUseCase.execute(any()) } returns cuisines

            val result = findRestaurantsService.execute(query)

            assertThat(result).isNotNull
            assertThat(result.list).isNotEmpty()
            assertThat(result.list).hasSize(10)
            assertThat(result.hasNext).isTrue()

            verify(exactly = 1) {
                findRestaurants.query(any())
                findTagsByIdsUseCase.execute(any())
                findNationalitiesByIdsUseCase.execute(any())
                findCuisinesByIdsUseCase.execute(any())
            }
        }
    }
}
