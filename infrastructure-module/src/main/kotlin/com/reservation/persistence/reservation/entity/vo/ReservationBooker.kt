package com.reservation.persistence.reservation.entity.vo

import com.reservation.persistence.converter.UuidBinaryConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import org.hibernate.annotations.Comment

@Embeddable
class ReservationBooker(
    @Convert(converter = UuidBinaryConverter::class)
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    @Comment("사용자 ID")
    val userId: String,
)
