package com.reservation.contract.event.timetable

import com.reservation.contract.event.EventJson
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant
import java.util.UUID

// SeatHeld는 contract-module이 발행하는 timetable 통합 이벤트의 JSON 와이어 형태를 고정한다.
// 이 테스트가 실패하면 봉투/eventType 태그가 소비자(payment 등) 몰래 바뀐 것이므로,
// 리터럴 단언을 완화하지 말고 계약 변경으로 취급한다(ADR-021, 02a §4-§5).
class SeatHeldEventJsonTest : BehaviorSpec({

    fun seatHeld(occurredAt: Instant = Instant.parse("2024-03-15T09:30:45.123456789Z")) =
        SeatHeld(
            eventId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            aggregateType = "Timetable",
            aggregateId = "timetable-1",
            sequenceNo = 7L,
            eventVersion = 1,
            occurredAt = occurredAt,
            correlationId = "correlation-1",
            causationId = "causation-1",
            traceparent = "00-trace-01",
            reservationId = "reservation-1",
            userId = "user-1",
            restaurantId = "restaurant-1",
        )

    given("SeatHeld 통합 이벤트가 주어졌을 때") {
        `when`("EventJson으로 직렬화하면") {
            val json = EventJson.encode(seatHeld())
            val normalized = json.replace(Regex("\\s+"), "")

            then("eventType 값이 \"SeatHeld\" 리터럴로 고정된다") {
                normalized shouldContain "\"eventType\":\"SeatHeld\""
            }

            then("봉투 10개 필드명이 모두 JSON 키로 존재한다") {
                val envelopeFields =
                    listOf(
                        "eventId",
                        "aggregateType",
                        "aggregateId",
                        "sequenceNo",
                        "eventType",
                        "eventVersion",
                        "occurredAt",
                        "correlationId",
                        "causationId",
                        "traceparent",
                    )

                envelopeFields.forEach { field ->
                    normalized shouldContain "\"$field\":"
                }
            }
        }

        `when`("직렬화 후 같은 타입으로 역직렬화하면") {
            val original = seatHeld()
            val json = EventJson.encode(original)
            val restored = EventJson.decode<SeatHeld>(json)

            then("원본 인스턴스와 완전히 동등하다") {
                restored shouldBe original
            }

            then("occurredAt의 나노초 정밀도가 손실 없이 복원된다") {
                restored.occurredAt shouldBe original.occurredAt
                restored.occurredAt.nano shouldBe original.occurredAt.nano
            }
        }

        `when`("nanoAdjustment가 0인 Instant로 왕복하면") {
            val original = seatHeld(occurredAt = Instant.parse("2024-03-15T09:30:45Z"))
            val restored = EventJson.decode<SeatHeld>(EventJson.encode(original))

            then("여전히 원본과 동등하다") {
                restored shouldBe original
            }
        }
    }
})
