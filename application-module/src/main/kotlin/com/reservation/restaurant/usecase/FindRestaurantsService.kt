package com.reservation.restaurant.usecase

import com.reservation.category.cuisine.port.input.FindCuisinesByIdsUseCase
import com.reservation.category.cuisine.port.input.query.request.FindCuisinesByIdsQuery
import com.reservation.category.nationality.port.input.FindNationalitiesByIdsUseCase
import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByIdsQuery
import com.reservation.category.tag.port.input.FindTagsByIdsUseCase
import com.reservation.category.tag.port.input.query.request.FindTagsByIdsQuery
import com.reservation.config.annotations.UseCase
import com.reservation.restaurant.port.input.FindRestaurantsUseCase
import com.reservation.restaurant.port.input.query.request.FindRestaurantsQueryRequest
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResult
import com.reservation.restaurant.port.input.query.response.FindRestaurantsQueryResults.FindRestaurantsQueryResultCategory
import com.reservation.restaurant.port.output.FindRestaurants
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsResult
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindRestaurantsService(
    private val findRestaurants: FindRestaurants,
    private val findTagsByIdsUseCase: FindTagsByIdsUseCase,
    private val findNationalitiesByIdsUseCase: FindNationalitiesByIdsUseCase,
    private val findCuisinesByIdsUseCase: FindCuisinesByIdsUseCase,
) : FindRestaurantsUseCase {
    @Transactional(readOnly = true)
    override fun execute(query: FindRestaurantsQueryRequest): FindRestaurantsQueryResults {
        val result = findRestaurants.query(query.toInquiry())

        val tags = result.flatMap { it.tags }
        val nationalities = result.flatMap { it.nationalities }
        val cuisines = result.flatMap { it.cuisines }

        val tagMap = provideTagMap(tags)
        val nationalitiesMap = provideNationalitiesMap(nationalities)
        val cuisinesMap = provideCuisinesMap(cuisines)

        val mappedResult = mapResult(result, tagMap, nationalitiesMap, cuisinesMap)
        return FindRestaurantsQueryResults.from(mappedResult)
    }

    private fun mapResult(
        list: List<FindRestaurantsResult>,
        tagMap: Map<Long, FindRestaurantsQueryResultCategory>,
        nationalitiesMap: Map<Long, FindRestaurantsQueryResultCategory>,
        cuisinesMap: Map<Long, FindRestaurantsQueryResultCategory>,
    ): List<FindRestaurantsQueryResult> {
        return list.map {
            val mappedTags =
                it.tags
                    .map { id: Long -> tagMap[id] }
                    .requireNoNulls()

            val mappedNationalities =
                it.nationalities
                    .map { id: Long -> nationalitiesMap[id] }
                    .requireNoNulls()

            val mappedCuisines =
                it.cuisines
                    .map { id: Long -> cuisinesMap[id] }
                    .requireNoNulls()

            it.toQuery(mappedTags, mappedNationalities, mappedCuisines)
        }
    }

    private fun provideTagMap(tags: List<Long>): Map<Long, FindRestaurantsQueryResultCategory> {
        return findTagsByIdsUseCase.execute(FindTagsByIdsQuery(tags))
            .map { FindRestaurantsQueryResultCategory(it.id, it.title, it.categoryType) }
            .associateBy { it.id }
    }

    private fun provideNationalitiesMap(
        nationalities: List<Long>,
    ): Map<Long, FindRestaurantsQueryResultCategory> {
        return findNationalitiesByIdsUseCase.execute(FindNationalitiesByIdsQuery(nationalities))
            .map { FindRestaurantsQueryResultCategory(it.id, it.title, it.categoryType) }
            .associateBy { it.id }
    }

    private fun provideCuisinesMap(
        cuisines: List<Long>,
    ): Map<Long, FindRestaurantsQueryResultCategory> {
        return findCuisinesByIdsUseCase.execute(FindCuisinesByIdsQuery(cuisines))
            .map { FindRestaurantsQueryResultCategory(it.id, it.title, it.categoryType) }
            .associateBy { it.id }
    }
}
