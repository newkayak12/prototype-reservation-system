package com.reservation.persistence.reservation.entity.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import org.hibernate.annotations.Comment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Embeddable
class ReservationTimeTableSchedule(
    @Column(name = "reservation_date")
    @Comment("시간표 날짜")
    val reservationDate: LocalDate,
    @Column(name = "reservation_day")
    @Comment("시간표 날짜 요일")
    @Enumerated(STRING)
    val reservationDay: DayOfWeek,
    @Column(name = "reservation_time")
    @Comment("시작 시간")
    val reservationTime: LocalTime,
)
