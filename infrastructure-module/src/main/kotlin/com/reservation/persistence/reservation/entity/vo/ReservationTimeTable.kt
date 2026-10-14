package com.reservation.persistence.reservation.entity.vo

import com.reservation.persistence.converter.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import org.hibernate.annotations.Comment

@Embeddable
class ReservationTimeTable(
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "timetable_id", columnDefinition = "BINARY(16)")
    @Comment("시간표 ID")
    val timeTableId: String,
    @Embedded
    val schedule: ReservationTimeTableSchedule,
    @Embedded
    val tableInformation: ReservationTimeTableInformation,
)
