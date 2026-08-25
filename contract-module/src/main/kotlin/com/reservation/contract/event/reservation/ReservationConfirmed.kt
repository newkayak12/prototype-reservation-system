package com.reservation.contract.event.reservation

import com.reservation.contract.event.AbstractEvent
import java.time.Instant
import java.util.UUID

// 페이로드 없음(마커 이벤트, ADR-021) — confirmedAt은 봉투 occurredAt과 항상 동일(동기 handle)해
// 제외하고, reservationId는 봉투 aggregateId와 항상 동일해 제외한다. 남는 서술 필드가 없다.
// 근거: docs/v2/modules/02b-contract-module-phase7-1-reservation-event-catalog-decision.md §4, §9.
@Suppress("LongParameterList")
data class ReservationConfirmed(
    override val eventId: UUID,
    override val aggregateType: String,
    override val aggregateId: String,
    override val sequenceNo: Long,
    override val eventType: String = ReservationEventTypes.RESERVATION_CONFIRMED,
    override val eventVersion: Int,
    override val occurredAt: Instant,
    override val correlationId: String,
    override val causationId: String?,
    override val traceparent: String?,
) : AbstractEvent
