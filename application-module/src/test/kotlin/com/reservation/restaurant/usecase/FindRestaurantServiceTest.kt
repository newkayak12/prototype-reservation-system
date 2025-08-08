package com.reservation.restaurant.usecase

import com.navercorp.fixturemonkey.kotlin.giveMe
import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.category.cuisine.port.input.FindCuisinesByIdsUseCase
import com.reservation.category.cuisine.port.input.query.response.FindCuisinesQueryResult
import com.reservation.category.nationality.port.input.FindNationalitiesByIdsUseCase
import com.reservation.category.nationality.port.input.query.response.FindNationalitiesQueryResult
import com.reservation.category.tag.port.input.FindTagsByIdsUseCase
import com.reservation.category.tag.port.input.query.response.FindTagsQueryResult
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.company.port.input.FindCompanyByIdNameUseCase
import com.reservation.company.port.input.query.response.FindCompaniesQueryResult
import com.reservation.fixture.FixtureMonkeyFactory
import com.reservation.restaurant.port.output.FindRestaurant
import com.reservation.restaurant.port.output.FindRestaurant.FindRestaurantResult
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@DisplayName("ID로 음식점을 단독 조회하고")
@ExtendWith(MockKExtension::class)
class FindRestaurantServiceTest {
    @DisplayName("내용물이 비어있으면")
    @Nested
    inner class EmptyResult {
        @MockK
        private lateinit var findRestaurant: FindRestaurant

        @MockK
        private lateinit var findCuisinesByIdsUseCase: FindCuisinesByIdsUseCase

        @MockK
        private lateinit var findNationalitiesByIdsUseCase: FindNationalitiesByIdsUseCase

        @MockK
        private lateinit var findTagsByIdsUseCase: FindTagsByIdsUseCase

        @MockK
        private lateinit var findCompanyByIdNameUseCase: FindCompanyByIdNameUseCase

        @InjectMockKs
        private lateinit var useCase: FindRestaurantService

        @DisplayName("NoSuchPersistedElementException를 리턴한다.")
        @Test
        fun `there is no result of restaurant then throw NoSuchPersistedElementException`() {
            val id = UuidGenerator.generate()
            every {
                findRestaurant.query(id)
            } throws NoSuchPersistedElementException()

            assertThrows<NoSuchPersistedElementException> {
                useCase.execute(id)
            }

            verify(exactly = 1) { findRestaurant.query(any()) }
        }
    }

    @DisplayName("내용물이 있고")
    @Nested
    inner class ResultExist {
        @MockK
        private lateinit var findRestaurant: FindRestaurant

        @MockK
        private lateinit var findCuisinesByIdsUseCase: FindCuisinesByIdsUseCase

        @MockK
        private lateinit var findNationalitiesByIdsUseCase: FindNationalitiesByIdsUseCase

        @MockK
        private lateinit var findTagsByIdsUseCase: FindTagsByIdsUseCase

        @MockK
        private lateinit var findCompanyByIdNameUseCase: FindCompanyByIdNameUseCase

        @InjectMockKs
        private lateinit var useCase: FindRestaurantService

        @DisplayName("조회에 성공하고 올바른 결과 값이 도출된다.")
        @Test
        fun `find proper element`() {
            val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()

            val id = UuidGenerator.generate()
            val findRestaurantResult =
                pureMonkey.giveMeBuilder<FindRestaurantResult>()
                    .set("identifier", id)
                    .set("cuisines", listOf(1L, 2L))
                    .set("nationalities", listOf(3L, 4L))
                    .set("tags", listOf(5L, 6L))
                    .sample()
            val cuisinesResult = pureMonkey.giveMe<FindCuisinesQueryResult>(5)
            val nationalitiesResult = pureMonkey.giveMe<FindNationalitiesQueryResult>(5)
            val tagsResult = pureMonkey.giveMe<FindTagsQueryResult>(5)
            val companiesResult = pureMonkey.giveMeOne<FindCompaniesQueryResult>()

            every {
                findRestaurant.query(id)
            } returns findRestaurantResult
            every {
                findCuisinesByIdsUseCase.execute(any())
            } returns cuisinesResult
            every {
                findNationalitiesByIdsUseCase.execute(any())
            } returns nationalitiesResult
            every {
                findTagsByIdsUseCase.execute(any())
            } returns tagsResult
            every {
                findCompanyByIdNameUseCase.execute(any())
            } returns companiesResult

            val result = useCase.execute(id)

            assertThat(result).isNotNull
            assertThat(result).extracting("identifier").isEqualTo(id)

            verify(exactly = 1) {
                findRestaurant.query(any())
                findCuisinesByIdsUseCase.execute(any())
                findNationalitiesByIdsUseCase.execute(any())
                findTagsByIdsUseCase.execute(any())
                findCompanyByIdNameUseCase.execute(any())
            }
        }
    }
}
