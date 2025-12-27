package com.reservation.persistence.reservation.entity.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.hibernate.annotations.Comment

@Embeddable
class ReservationRestaurant(
    @Column(name = "restaurant_company_id")
    @Comment("매장 ID")
    val restaurantId: String,
)
