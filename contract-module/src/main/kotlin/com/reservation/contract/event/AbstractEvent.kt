package com.reservation.contract.event

import java.time.Instant
import java.util.UUID

interface AbstractEvent {
    val eventId: UUID
    val aggregateType: String
    val aggregateId: String
    val sequenceNo: Long
    val eventType: String
    val eventVersion: Int
    val occurredAt: Instant
    val correlationId: String
    val causationId: String?
    val traceparent: String?
}
