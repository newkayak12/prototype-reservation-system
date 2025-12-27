package com.reservation.event.abstractEvent

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.reservation.enumeration.OutboxEventType

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@class",
)
sealed interface AbstractEvent {
    val eventType: OutboxEventType
    val eventVersion: Double

    fun key(): String
}
