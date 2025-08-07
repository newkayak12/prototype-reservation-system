package com.reservation.persistence.restaurant.repository.dsl

import com.querydsl.core.group.GroupBy.groupBy
import com.querydsl.core.group.GroupBy.list
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.restaurant.QRestaurantCuisinesEntity.restaurantCuisinesEntity
import com.reservation.persistence.restaurant.QRestaurantEntity.restaurantEntity
import com.reservation.persistence.restaurant.QRestaurantNationalitiesEntity.restaurantNationalitiesEntity
import com.reservation.persistence.restaurant.QRestaurantPhotoEntity.restaurantPhotoEntity
import com.reservation.persistence.restaurant.QRestaurantTagsEntity.restaurantTagsEntity
import com.reservation.persistence.restaurant.QRestaurantWorkingDayEntity.restaurantWorkingDayEntity
import com.reservation.restaurant.port.output.FindRestaurant
import com.reservation.restaurant.port.output.FindRestaurant.FindRestaurantPhoto
import com.reservation.restaurant.port.output.FindRestaurant.FindRestaurantResult
import com.reservation.restaurant.port.output.FindRestaurant.FindRestaurantWorkingDay
import org.springframework.stereotype.Component

@Component
class FindRestaurantRepository(
    private val query: JPAQueryFactory,
) : FindRestaurant {
    override fun query(id: String): FindRestaurantResult? {
        return query.select()
            .from(restaurantEntity)
            .join(restaurantWorkingDayEntity)
            .on(restaurantEntity.identifier.eq(restaurantWorkingDayEntity.restaurant.identifier))
            .join(restaurantPhotoEntity)
            .on(restaurantEntity.identifier.eq(restaurantPhotoEntity.restaurant.identifier))
            .join(restaurantNationalitiesEntity)
            .on(restaurantEntity.identifier.eq(restaurantNationalitiesEntity.restaurant.identifier))
            .join(restaurantCuisinesEntity)
            .on(restaurantEntity.identifier.eq(restaurantCuisinesEntity.restaurant.identifier))
            .join(restaurantTagsEntity)
            .on(restaurantEntity.identifier.eq(restaurantTagsEntity.restaurant.identifier))
            .where(
                RestaurantQuerySpec.identifierEq(id),
                RestaurantQuerySpec.isNotDeleted(),
            )
            .transform(
                groupBy(restaurantEntity.identifier).`as`(
                    Projections.constructor(
                        FindRestaurantResult::class.java,
                        restaurantEntity.identifier,
                        restaurantEntity.companyId,
                        restaurantEntity.userId,
                        restaurantEntity.name,
                        restaurantEntity.introduce,
                        restaurantEntity.phone,
                        restaurantEntity.zipCode,
                        restaurantEntity.address,
                        restaurantEntity.detail,
                        restaurantEntity.latitude,
                        restaurantEntity.longitude,
                        list(
                            Projections.constructor(
                                FindRestaurantWorkingDay::class.java,
                                restaurantWorkingDayEntity.id.day,
                                restaurantWorkingDayEntity.startTime,
                                restaurantWorkingDayEntity.endTime,
                            ),
                        ),
                        list(
                            Projections.constructor(
                                FindRestaurantPhoto::class.java,
                                restaurantPhotoEntity.url,
                            ),
                        ),
                        list(restaurantTagsEntity.tagsId),
                        list(restaurantNationalitiesEntity.nationalitiesId),
                        list(restaurantCuisinesEntity.cuisinesId),
                    ),
                ),
            ).values.firstOrNull()
    }
}
