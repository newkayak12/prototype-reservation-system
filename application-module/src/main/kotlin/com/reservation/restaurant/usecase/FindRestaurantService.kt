package com.reservation.restaurant.usecase

import com.reservation.category.cuisine.port.input.FindCuisinesByIdsUseCase
import com.reservation.category.cuisine.port.input.query.request.FindCuisinesByIdsQuery
import com.reservation.category.nationality.port.input.FindNationalitiesByIdsUseCase
import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByIdsQuery
import com.reservation.category.tag.port.input.FindTagsByIdsUseCase
import com.reservation.category.tag.port.input.query.request.FindTagsByIdsQuery
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.company.port.input.FindCompanyByIdNameUseCase
import com.reservation.company.port.input.query.request.FindCompaniesByIdQuery
import com.reservation.config.annotations.UseCase
import com.reservation.restaurant.port.input.FindRestaurantUseCase
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult.FindRestaurantQueryResultAddress
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult.FindRestaurantQueryResultCompany
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult.FindRestaurantQueryResultCoordinate
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult.FindRestaurantQueryResultInformation
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult.FindRestaurantQueryResultPhoto
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult.FindRestaurantQueryResultTag
import com.reservation.restaurant.port.input.query.response.FindRestaurantQueryResult.FindRestaurantQueryResultWorkingDay
import com.reservation.restaurant.port.output.FindRestaurant
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindRestaurantService(
    private val findRestaurant: FindRestaurant,
    private val findCuisinesByIdsUseCase: FindCuisinesByIdsUseCase,
    private val findNationalitiesByIdsUseCase: FindNationalitiesByIdsUseCase,
    private val findTagsByIdsUseCase: FindTagsByIdsUseCase,
    private val findCompanyByIdNameUseCase: FindCompanyByIdNameUseCase,
) : FindRestaurantUseCase {
    private fun findCuisines(cuisines: List<Long>) =
        findCuisinesByIdsUseCase.execute(FindCuisinesByIdsQuery(cuisines))
            .map {
                FindRestaurantQueryResultTag(
                    it.id,
                    it.title,
                    it.categoryType,
                )
            }

    private fun findNationalities(nationalities: List<Long>) =
        findNationalitiesByIdsUseCase.execute(FindNationalitiesByIdsQuery(nationalities))
            .map {
                FindRestaurantQueryResultTag(
                    it.id,
                    it.title,
                    it.categoryType,
                )
            }

    private fun findTags(tags: List<Long>) =
        findTagsByIdsUseCase.execute(FindTagsByIdsQuery(tags))
            .map {
                FindRestaurantQueryResultTag(
                    it.id,
                    it.title,
                    it.categoryType,
                )
            }

    private fun findCompany(companyId: String) =
        findCompanyByIdNameUseCase.execute(FindCompaniesByIdQuery(companyId))

    @Transactional(readOnly = true)
    override fun execute(id: String): FindRestaurantQueryResult {
        val restaurantResult = findRestaurant.query(id) ?: throw NoSuchPersistedElementException()
        val cuisines = findCuisines(restaurantResult.cuisines)
        val nationalities = findNationalities(restaurantResult.nationalities)
        val tags = findTags(restaurantResult.tags)
        val company = findCompany(restaurantResult.companyId)

        return FindRestaurantQueryResult(
            identifier = restaurantResult.identifier,
            company =
                FindRestaurantQueryResultCompany(
                    companyId = company.id,
                    companyName = company.brandName,
                ),
            userId = restaurantResult.userId,
            information =
                FindRestaurantQueryResultInformation(
                    name = restaurantResult.name,
                    introduce = restaurantResult.introduce,
                ),
            phone = restaurantResult.phone,
            address =
                FindRestaurantQueryResultAddress(
                    zipCode = restaurantResult.zipCode,
                    address = restaurantResult.address,
                    detail = restaurantResult.detail,
                    coordinate =
                        FindRestaurantQueryResultCoordinate(
                            latitude = restaurantResult.latitude,
                            longitude = restaurantResult.longitude,
                        ),
                ),
            workingDays =
                restaurantResult.workingDays.map {
                    FindRestaurantQueryResultWorkingDay(
                        it.day,
                        it.startTime,
                        it.endTime,
                    )
                },
            photos = restaurantResult.photos.map { FindRestaurantQueryResultPhoto(it.url) },
            cuisines = cuisines,
            nationalities = nationalities,
            tags = tags,
        )
    }
}
