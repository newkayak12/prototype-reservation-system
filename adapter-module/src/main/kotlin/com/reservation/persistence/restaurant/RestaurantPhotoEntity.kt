package com.reservation.persistence.restaurant

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
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
    @ManyToOne()
    @JoinColumn(name = "restaurant_id", nullable = false, updatable = false)
    val restaurant: RestaurantEntity,
    @Column(name = "url", nullable = false, updatable = false)
    val url: String,
) : TimeBasedPrimaryKey()
