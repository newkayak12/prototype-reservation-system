package com.reservation.persistence.timetable.entity

import com.reservation.enumeration.TableStatus
import com.reservation.enumeration.TableStatus.EMPTY
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Table(
    catalog = "prototype_reservation",
    name = "timetable",
    indexes = [],
)
@Entity
@Suppress("LongParameterList")
class TimeTableEntity(
    @Column(name = "restaurant_id")
    val restaurantId: String,
    @Column(name = "date")
    val date: LocalDate,
    @field:Enumerated(STRING)
    @Column(name = "day")
    val day: DayOfWeek,
    @Column(name = "start_time")
    val startTime: LocalTime,
    @Column(name = "end_time")
    val endTime: LocalTime,
    @Column(name = "table_number")
    val tableNumber: Int,
    @Column(name = "table_size")
    val tableSize: Int,
    @field:Enumerated(STRING)
    @Column(name = "table_status")
    val tableStatus: TableStatus = EMPTY,
) : TimeBasedPrimaryKey()
