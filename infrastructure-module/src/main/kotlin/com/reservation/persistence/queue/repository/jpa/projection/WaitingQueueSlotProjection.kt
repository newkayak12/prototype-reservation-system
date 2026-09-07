package com.reservation.persistence.queue.repository.jpa.projection

import java.time.LocalDate
import java.time.LocalTime

interface WaitingQueueSlotProjection {
    fun getRestaurantId(): String

    fun getDate(): LocalDate

    fun getStartTime(): LocalTime
}
