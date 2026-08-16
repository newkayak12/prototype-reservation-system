package com.reservation.contract.event.timetable

import com.reservation.contract.event.AbstractEvent
import java.time.Instant
import java.util.UUID

// 페이로드는 payment가 실제로 소비하는 3필드만 싣는다(얇은 통합 이벤트, ADR-021) —
// slotId/date/startTime/endTime/tableNumber/heldAt/holdExpiresAt은 봉투 중복이거나 소비 근거가 없어 제외.
// 근거: docs/v2/modules/02a-contract-module-phase7-1-event-catalog-decision.md §4-§5.
@Suppress("LongParameterList")
data class SeatHeld(
    override val eventId: UUID,
    override val aggregateType: String,
    override val aggregateId: String,
    override val sequenceNo: Long,
    override val eventType: String = TimetableEventTypes.SEAT_HELD,
    override val eventVersion: Int,
    override val occurredAt: Instant,
    override val correlationId: String,
    override val causationId: String?,
    override val traceparent: String?,
    val reservationId: String,
    val userId: String,
    val restaurantId: String,
) : AbstractEvent
