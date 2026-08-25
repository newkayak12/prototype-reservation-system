package com.reservation.contract.event.reservation

import com.reservation.contract.event.AbstractEvent
import java.time.Instant
import java.util.UUID

// reservationId는 봉투 aggregateId와 항상 동일해 제외하고, cancelledAt은 봉투 occurredAt과
// 항상 동일(동기 handle)해 제외한다. cancelledBy·reason은 query-module 소비 근거로 채택한다.
// 근거: docs/v2/modules/02b-contract-module-phase7-1-reservation-event-catalog-decision.md §4, §5, §9.
@Suppress("LongParameterList")
data class ReservationCancelled(
    override val eventId: UUID,
    override val aggregateType: String,
    override val aggregateId: String,
    override val sequenceNo: Long,
    override val eventType: String = ReservationEventTypes.RESERVATION_CANCELLED,
    override val eventVersion: Int,
    override val occurredAt: Instant,
    override val correlationId: String,
    override val causationId: String?,
    override val traceparent: String?,
    val cancelledBy: CancelledBy,
    val reason: String?,
) : AbstractEvent
