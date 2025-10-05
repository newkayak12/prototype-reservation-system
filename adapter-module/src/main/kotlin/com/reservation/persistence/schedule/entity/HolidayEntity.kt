package com.reservation.persistence.schedule.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Table(
    catalog = "prototype_reservation",
    name = "holiday",
    indexes = [],
)
@Entity
class HolidayEntity(
    @Column(name = "restaurant_id")
    val restaurantId: String,
    date: LocalDate,
) : TimeBasedPrimaryKey() {

    @Column(name = "date")
    var date: LocalDate = date
        protected set

}
