package com.reservation.persistence.restaurant

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Table(
    catalog = "prototype_reservation",
    name = "restaurant_photo",
    indexes = [],
)
@Entity
class RestaurantPhotoEntity(
    @ManyToOne
    @JoinColumn(name = "restaurant_id")
    private var restaurant: RestaurantEntity,
    private var url: String,
) : TimeBasedPrimaryKey()
