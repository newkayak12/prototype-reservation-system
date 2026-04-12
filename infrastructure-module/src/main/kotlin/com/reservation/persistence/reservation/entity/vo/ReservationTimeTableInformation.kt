package com.reservation.persistence.reservation.entity.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.hibernate.annotations.Comment

@Embeddable
class ReservationTimeTableInformation(
    @Column(name = "reservation_seat_size")
    @Comment("테이블 사이즈")
    val reservationSeatSize: Int,
)
