package com.reservation.contract.event.reservation

import com.reservation.contract.event.EventJson
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

// ReservationCreated는 contract-module이 발행하는 reservation 통합 이벤트의 JSON 와이어 형태를 고정한다.
// 골든 문자열은 자기참조 왕복이 아니라 동결된 리터럴이다 — 필드명·키 순서·eventType 리터럴이 소비자
// 몰래 바뀌면 이 테스트가 실패해야 하므로, 실패 시 리터럴을 완화하지 말고 계약 변경으로 취급한다
// (ADR-022, RFC-029, ADR-010/RFC-022, ADR-021).
class ReservationCreatedEventJsonTest : BehaviorSpec({

    val fixedOccurredAt: Instant = Instant.parse("2024-03-15T09:30:45.123456789Z")

    fun reservationCreated(occurredAt: Instant = fixedOccurredAt) =
        ReservationCreated(
            eventId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            aggregateType = "Reservation",
            aggregateId = "reservation-1",
            sequenceNo = 1L,
            eventVersion = 1,
            occurredAt = occurredAt,
            correlationId = "correlation-1",
            causationId = "causation-1",
            traceparent = "00-trace-01",
            userId = "user-1",
            restaurantId = "restaurant-1",
            tableNumber = 5,
            slotId = "slot-1",
            date = LocalDate.of(2024, 3, 15),
            startTime = LocalTime.of(18, 0),
            endTime = LocalTime.of(20, 0),
        )

    val goldenJson =
        """
        {"eventId":"11111111-1111-1111-1111-111111111111","aggregateType":"Reservation","aggregateId":"reservation-1","sequenceNo":1,"eventType":"ReservationCreated","eventVersion":1,"occurredAt":"2024-03-15T09:30:45.123456789Z","correlationId":"correlation-1","causationId":"causation-1","traceparent":"00-trace-01","userId":"user-1","restaurantId":"restaurant-1","tableNumber":5,"slotId":"slot-1","date":"2024-03-15","startTime":"18:00:00","endTime":"20:00:00"}
        """.trimIndent()

    given("ReservationCreated 통합 이벤트가 주어졌을 때") {
        `when`("EventJson으로 직렬화하면") {
            val json = EventJson.encode(reservationCreated())
            val normalized = json.replace(Regex("\\s+"), "")

            then("동결된 골든 JSON 문자열과 완전히 동일하다(필드명·키 순서·eventType 리터럴 동시 고정)") {
                normalized shouldBe goldenJson
            }
        }

        `when`("직렬화 후 같은 타입으로 역직렬화하면") {
            val original = reservationCreated()
            val json = EventJson.encode(original)
            val restored = EventJson.decode<ReservationCreated>(json)

            then("원본 인스턴스와 완전히 동등하다") {
                restored shouldBe original
            }

            then("occurredAt의 나노초 정밀도가 손실 없이 복원된다") {
                restored.occurredAt shouldBe original.occurredAt
                restored.occurredAt.nano shouldBe original.occurredAt.nano
            }
        }

        `when`("nanoAdjustment가 0인 Instant로 왕복하면") {
            val original = reservationCreated(occurredAt = Instant.parse("2024-03-15T09:30:45Z"))
            val restored = EventJson.decode<ReservationCreated>(EventJson.encode(original))

            then("여전히 원본과 동등하다") {
                restored shouldBe original
            }
        }
    }
})
