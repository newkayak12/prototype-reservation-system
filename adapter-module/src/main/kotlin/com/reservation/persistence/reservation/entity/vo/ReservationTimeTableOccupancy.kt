package com.reservation.persistence.reservation.entity.vo

import com.reservation.enumeration.ReservationStatus
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Embeddable
class ReservationTimeTableOccupancy(
    @Column(name = "timetable_occupancy_id")
    @Comment("시간표 점유 ID")
    val timetableOccupancyId: String,
    @Column(name = "reservation_status")
    @Comment("예약 상태")
    val reservationStatus: ReservationStatus,
    @Column(name = "reservation_occupied_datetime")
    @Comment("예약 시간")
    val reservationOccupiedDatetime: LocalDateTime,
    @Column(name = "reservation_cancelled_datetime")
    @Comment("취소 시간")
    val reservationCancelledDatetime: LocalDateTime? = null,
)
