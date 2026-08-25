package com.reservation.contract.event.reservation

// eventType 태그 값은 클래스 FQCN이 아닌 논리 타입명 문자열이며, 클래스명 리팩터링과 분리돼 있다 —
// 발행되는 순간 되돌릴 수 없는 one-way door다(ADR-010 결정 #2 · 02a §8 '가역성').
object ReservationEventTypes {
    const val RESERVATION_CREATED: String = "ReservationCreated"
    const val RESERVATION_CONFIRMED: String = "ReservationConfirmed"
    const val RESERVATION_FAILED: String = "ReservationFailed"
    const val RESERVATION_EXPIRED: String = "ReservationExpired"
    const val RESERVATION_CANCELLED: String = "ReservationCancelled"
    const val RESERVATION_NO_SHOW: String = "ReservationNoShow"
    const val REFUND_REQUIRED: String = "RefundRequired"
}
