package com.reservation.persistence.restaurant.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Table(
    catalog = "prototype_reservation",
    name = "restaurant_photo",
    indexes = [],
)
@Entity
class RestaurantPhotoEntity(
    @ManyToOne
    @JoinColumn(
        name = "restaurant_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(NO_CONSTRAINT),
    )
    @Comment("음식점 식별키")
    val restaurant: RestaurantEntity,
    @Column(name = "url", nullable = false, updatable = false)
    @Comment("URL")
    val url: String,
) : TimeBasedPrimaryKey()
