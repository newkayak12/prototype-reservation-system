package com.reservation.contract.event.reservation

import com.reservation.contract.event.AbstractEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// 페이로드는 query-module이 계약-전용으로 소비하는 7필드만 싣는다(얇은 통합 이벤트, ADR-021) —
// reservationId(봉투 aggregateId와 항상 동일)·tableSize·day(date 파생 중복)·requestedAt은
// 소비 근거가 없거나 봉투 중복이라 제외.
// 근거: docs/v2/modules/02b-contract-module-phase7-1-reservation-event-catalog-decision.md §4, §9.
@Suppress("LongParameterList")
data class ReservationCreated(
    override val eventId: UUID,
    override val aggregateType: String,
    override val aggregateId: String,
    override val sequenceNo: Long,
    override val eventType: String = ReservationEventTypes.RESERVATION_CREATED,
    override val eventVersion: Int,
    override val occurredAt: Instant,
    override val correlationId: String,
    override val causationId: String?,
    override val traceparent: String?,
    val userId: String,
    val restaurantId: String,
    val tableNumber: Int,
    val slotId: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
) : AbstractEvent
