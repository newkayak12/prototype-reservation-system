package com.reservation.persistence.restaurant.entity

import com.reservation.persistence.converter.UuidBinaryIdentifierType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import org.hibernate.annotations.Comment
import org.hibernate.annotations.Type
import java.io.Serializable
import java.time.DayOfWeek

/**
 * ADR-028 결정 3, 폴백 B: `@EmbeddedId` 구성요소도 `@Id`와 동일 제약을
 * 받는다 — JPA `@Convert`가 아니라 [UuidBinaryIdentifierType](Hibernate
 * `UserType`)으로 변환한다. `BinaryPkHibernateVerificationTest`로
 * `@MapsId`가 공유하는 FK 컬럼까지 포함해 확인.
 */
@Embeddable
data class RestaurantWorkingDayId(
    @Type(UuidBinaryIdentifierType::class)
    @Column(
        name = "restaurant_id",
        columnDefinition = "BINARY(16)",
        nullable = false,
        updatable = false,
    )
    @Comment("식별키")
    val identifier: String,
    @Column(name = "day")
    @Enumerated(STRING)
    @Comment("요일")
    val day: DayOfWeek,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
