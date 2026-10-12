package com.reservation.persistence.schedule.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import com.reservation.persistence.converter.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.LocalTime

@Table(
    catalog = "prototype_reservation",
    name = "time_span",
    indexes = [],
)
@Entity
class TimeSpanEntity(
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "restaurant_id", columnDefinition = "BINARY(16)")
    val restaurantId: String,
    day: DayOfWeek,
    startTime: LocalTime,
    endTime: LocalTime,
) : TimeBasedPrimaryKey() {
    @field:Enumerated(STRING)
    @Column(name = "day")
    var day: DayOfWeek = day
        protected set

    @Column(name = "start_time")
    var startTime: LocalTime = startTime
        protected set

    @Column(name = "end_time")
    var endTime: LocalTime = endTime
        protected set
}
