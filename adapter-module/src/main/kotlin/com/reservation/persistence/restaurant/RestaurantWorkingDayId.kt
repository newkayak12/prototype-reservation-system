package com.reservation.persistence.restaurant

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import org.hibernate.annotations.Comment
import java.io.Serializable
import java.time.DayOfWeek

@Embeddable
data class RestaurantWorkingDayId(
    @Column(
        name = "restaurant_id",
        columnDefinition = "VARCHAR(128)",
        nullable = false,
        updatable = false,
    )
    @Comment("식별키")
    val identifier: String,
    @Column(name = "day_of_month")
    @Enumerated(STRING)
    @Comment("요일")
    val day: DayOfWeek,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
