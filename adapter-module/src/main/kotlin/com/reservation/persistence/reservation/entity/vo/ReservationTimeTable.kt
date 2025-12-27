package com.reservation.persistence.reservation.entity.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import org.hibernate.annotations.Comment

@Embeddable
class ReservationTimeTable(
    @Column(name = "timetable_id")
    @Comment("시간표 ID")
    val timeTableId: String,
    @Embedded
    val schedule: ReservationTimeTableSchedule,
    @Embedded
    val tableInformation: ReservationTimeTableInformation,
)
