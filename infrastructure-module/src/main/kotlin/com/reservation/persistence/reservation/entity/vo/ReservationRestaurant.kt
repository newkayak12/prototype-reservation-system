package com.reservation.persistence.reservation.entity.vo

import com.reservation.persistence.converter.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import org.hibernate.annotations.Comment

@Embeddable
class ReservationRestaurant(
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "restaurant_id", columnDefinition = "BINARY(16)")
    @Comment("매장 ID")
    val restaurantId: String,
)
