package com.reservation.persistence.schedule.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import com.reservation.persistence.converter.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
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
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "restaurant_id", columnDefinition = "BINARY(16)")
    val restaurantId: String,
    date: LocalDate,
) : TimeBasedPrimaryKey() {
    @Column(name = "date")
    var date: LocalDate = date
        protected set
}
