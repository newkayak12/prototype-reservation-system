package com.reservation.persistence.restaurant.repository.dsl

import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.restaurant.entity.QRestaurantCuisinesEntity.restaurantCuisinesEntity
import com.reservation.persistence.restaurant.entity.QRestaurantEntity.restaurantEntity
import com.reservation.persistence.restaurant.entity.QRestaurantNationalitiesEntity.restaurantNationalitiesEntity
import com.reservation.persistence.restaurant.entity.QRestaurantPhotoEntity.restaurantPhotoEntity
import com.reservation.persistence.restaurant.entity.QRestaurantTagsEntity.restaurantTagsEntity
import com.reservation.persistence.restaurant.entity.QRestaurantWorkingDayEntity.restaurantWorkingDayEntity
import com.reservation.restaurant.port.output.FindRestaurants
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsInquiry
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsResult
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsResultAddress
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsResultCoordinate
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsResultInformation
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsResultPhoto
import com.reservation.restaurant.port.output.FindRestaurants.FindRestaurantsResultWorkingDay
import kotlin.collections.Map.Entry
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class FindRestaurantsRepository(
    private val query: JPAQueryFactory,
) : FindRestaurants {
    private fun workingDays(
        identifiers: List<String>,
    ): Map<String, List<FindRestaurantsResultWorkingDay>> {
        return query.select()
            .from(restaurantWorkingDayEntity)
            .where(
                RestaurantWorkingDaysQuerySpec.workingDayRestaurantIdentifiersIn(
                    identifiers.toSet(),
                ),
            )
            .fetch()
            .groupBy { it[restaurantWorkingDayEntity.restaurant.identifier] ?: "" }
            .mapValues { (_, values) ->
                values.mapNotNull { tuple ->
                    val day = tuple[restaurantWorkingDayEntity.id.day]
                    val startTime = tuple[restaurantWorkingDayEntity.startTime]
                    val endTime = tuple[restaurantWorkingDayEntity.endTime]

                    if (day != null && startTime != null && endTime != null) {
                        FindRestaurantsResultWorkingDay(
                            day = day,
                            startTime = startTime,
                            endTime = endTime,
                        )
                    } else {
                        null
                    }
                }
            }
    }

    private fun photos(identifiers: List<String>): Map<String, List<FindRestaurantsResultPhoto>> {
        return query.select()
            .from(restaurantPhotoEntity)
            .where(
                RestaurantPhotoQuerySpec.photoRestaurantIdentifiersIn(identifiers.toSet()),
            )
            .fetch()
            .groupBy { it[restaurantPhotoEntity.restaurant.identifier] ?: "" }
            .mapValues { (_, values) ->
                values.mapNotNull { tuple ->
                    val url = tuple[restaurantPhotoEntity.url]
                    if (url != null) {
                        FindRestaurantsResultPhoto(url)
                    } else {
                        null
                    }
                }
            }
    }

    private fun transform(
        tuples: List<Tuple>,
        workingDaysMap: Map<String, List<FindRestaurantsResultWorkingDay>>,
        photosMap: Map<String, List<FindRestaurantsResultPhoto>>,
    ): List<FindRestaurantsResult> {
        return tuples.groupBy { it[restaurantEntity.identifier] }
            .map { entry: Entry<String?, List<Tuple>> ->
                val identifier = entry.key
                val values = entry.value
                val restaurantTuple = values.first()

                val tagsId = values.mapNotNull { it[restaurantTagsEntity.tagsId] }
                val nationalitiesId =
                    values
                        .mapNotNull { it[restaurantNationalitiesEntity.nationalitiesId] }
                val cuisinesId = values.mapNotNull { it[restaurantCuisinesEntity.cuisinesId] }

                val workingDays = workingDaysMap[identifier] ?: listOf()
                val photos = photosMap[identifier] ?: listOf()

                FindRestaurantsResult(
                    identifier = identifier ?: "",
                    information =
                        FindRestaurantsResultInformation(
                            name = restaurantTuple[restaurantEntity.name] ?: "",
                            introduce = restaurantTuple[restaurantEntity.introduce] ?: "",
                        ),
                    address =
                        FindRestaurantsResultAddress(
                            zipCode = restaurantTuple[restaurantEntity.zipCode] ?: "",
                            address = restaurantTuple[restaurantEntity.address] ?: "",
                            detail = restaurantTuple[restaurantEntity.detail] ?: "",
                            coordinate =
                                FindRestaurantsResultCoordinate(
                                    latitude =
                                        restaurantTuple[restaurantEntity.latitude]
                                            ?: BigDecimal.ZERO,
                                    longitude =
                                        restaurantTuple[restaurantEntity.longitude]
                                            ?: BigDecimal.ZERO,
                                ),
                        ),
                    workingDays = workingDays,
                    photos = photos,
                    tags = tagsId,
                    nationalities = nationalitiesId,
                    cuisines = cuisinesId,
                )
            }
    }

    override fun query(inquiry: FindRestaurantsInquiry): List<FindRestaurantsResult> {
        val tuples =
            query.select()
                .from(restaurantEntity)
                .join(restaurantNationalitiesEntity)
                .on(
                    restaurantEntity.identifier
                        .eq(restaurantNationalitiesEntity.restaurant.identifier),
                )
                .join(restaurantCuisinesEntity)
                .on(
                    restaurantEntity.identifier
                        .eq(restaurantCuisinesEntity.restaurant.identifier),
                )
                .join(restaurantTagsEntity)
                .on(
                    restaurantEntity.identifier
                        .eq(restaurantTagsEntity.restaurant.identifier),
                )
                .where(
                    RestaurantQuerySpec.isNotDeleted(),
                    RestaurantQuerySpec.identifierAfter(inquiry.identifierFrom),
                    RestaurantCategoryQuerySpec.tagsIn(inquiry.tags.toSet()),
                    RestaurantCategoryQuerySpec.nationalitiesIn(inquiry.nationalities.toSet()),
                    RestaurantCategoryQuerySpec.cuisinesIn(inquiry.cuisines.toSet()),
                    RestaurantQuerySpec.nameContains(inquiry.searchText),
                )
                .limit(inquiry.size)
                .orderBy(restaurantEntity.identifier.desc())
                .fetch()

        val restaurantIdentifiers: List<String> =
            tuples
                .distinctBy { it[restaurantEntity.identifier] }
                .mapNotNull { it[restaurantEntity.identifier] }
        val workingDaysMap = workingDays(restaurantIdentifiers)
        val photoMap = photos(restaurantIdentifiers)

        return transform(tuples, workingDaysMap, photoMap)
    }
}
