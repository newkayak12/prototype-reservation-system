package com.reservation.contract.event.timetable

// eventType 태그 값은 클래스 FQCN이 아닌 논리 타입명 문자열이며, 클래스명 리팩터링과 분리돼 있다 —
// 발행되는 순간 되돌릴 수 없는 one-way door다(ADR-010 결정 #2 · 02a §8 '가역성').
object TimetableEventTypes {
    const val SEAT_HELD: String = "SeatHeld"
}
