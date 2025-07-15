package com.reservation.persistence.restaurant

import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalTime

@Table(
    catalog = "prototype_reservation",
    name = "restaurant_working_day",
    indexes = [],
)
@Entity
class RestaurantWorkingDayEntity(
    @EmbeddedId
    val id: RestaurantWorkingDayId,
    @MapsId("identifier")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "restaurant_id",
        insertable = true,
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(value = NO_CONSTRAINT),
    )
    val restaurant: RestaurantEntity,
    @Column(name = "start_time")
    @Comment("시작 시간")
    var startTime: LocalTime,
    @Column(name = "end_time")
    @Comment("종료 시간")
    var endTime: LocalTime,
)
