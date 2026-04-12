package com.reservation.persistence.reservation.entity.vo

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import org.hibernate.annotations.Comment

@Embeddable
class ReservationBooker(
    @Column(name = "user_id")
    @Comment("사용자 ID")
    val userId: String,
)
