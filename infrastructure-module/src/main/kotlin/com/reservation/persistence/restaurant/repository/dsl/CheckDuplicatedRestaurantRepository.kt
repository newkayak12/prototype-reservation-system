package com.reservation.persistence.restaurant.repository.dsl

import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.restaurant.entity.QRestaurantEntity.restaurantEntity
import com.reservation.restaurant.port.output.CheckRestaurantDuplicated
import com.reservation.restaurant.port.output.CheckRestaurantDuplicated.CheckRestaurantDuplicatedInquiry
import org.springframework.stereotype.Component

@Component
class CheckDuplicatedRestaurantRepository(
    private val query: JPAQueryFactory,
) : CheckRestaurantDuplicated {
    override fun query(inquiry: CheckRestaurantDuplicatedInquiry): Boolean {
        return query
            .select(restaurantEntity.identifier)
            .from(restaurantEntity)
            .where(
                RestaurantQuerySpec.companyIdEq(inquiry.companyId),
                RestaurantQuerySpec.nameEq(inquiry.name),
            )
            .fetchFirst() != null
    }
}
