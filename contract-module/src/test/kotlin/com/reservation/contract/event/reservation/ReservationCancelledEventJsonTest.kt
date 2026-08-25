package com.reservation.contract.event.reservation

import com.reservation.contract.event.EventJson
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

// ReservationCancelled는 contract-module이 발행하는 reservation 통합 이벤트의 JSON 와이어 형태를 고정한다.
// 골든 문자열은 자기참조 왕복이 아니라 동결된 리터럴이다 — 필드명·키 순서·eventType 리터럴이 소비자
// 몰래 바뀌면 이 테스트가 실패해야 하므로, 실패 시 리터럴을 완화하지 말고 계약 변경으로 취급한다
// (ADR-022, RFC-029, ADR-010/RFC-022, ADR-021).
class ReservationCancelledEventJsonTest : BehaviorSpec({

    val fixedOccurredAt: Instant = Instant.parse("2024-03-15T09:30:45.123456789Z")

    fun reservationCancelled(
        cancelledBy: CancelledBy,
        reason: String?,
        occurredAt: Instant = fixedOccurredAt,
    ) = ReservationCancelled(
        eventId = UUID.fromString("66666666-6666-6666-6666-666666666666"),
        aggregateType = "Reservation",
        aggregateId = "reservation-6",
        sequenceNo = 6L,
        eventVersion = 1,
        occurredAt = occurredAt,
        correlationId = "correlation-6",
        causationId = "causation-6",
        traceparent = "00-trace-06",
        cancelledBy = cancelledBy,
        reason = reason,
    )

    val guestGoldenJson =
        """
        {"eventId":"66666666-6666-6666-6666-666666666666","aggregateType":"Reservation","aggregateId":"reservation-6","sequenceNo":6,"eventType":"ReservationCancelled","eventVersion":1,"occurredAt":"2024-03-15T09:30:45.123456789Z","correlationId":"correlation-6","causationId":"causation-6","traceparent":"00-trace-06","cancelledBy":"GUEST","reason":null}
        """.trimIndent()

    val ownerGoldenJson =
        """
        {"eventId":"66666666-6666-6666-6666-666666666666","aggregateType":"Reservation","aggregateId":"reservation-6","sequenceNo":6,"eventType":"ReservationCancelled","eventVersion":1,"occurredAt":"2024-03-15T09:30:45.123456789Z","correlationId":"correlation-6","causationId":"causation-6","traceparent":"00-trace-06","cancelledBy":"OWNER","reason":"매장사정으로인한임시휴무로예약을취소합니다긴급설비점검이필요합니다"}
        """.trimIndent()

    given("GUEST가 취소한 ReservationCancelled 통합 이벤트가 주어졌을 때") {
        fun guestReservationCancelled(occurredAt: Instant = fixedOccurredAt) =
            reservationCancelled(
                cancelledBy = CancelledBy.GUEST,
                reason = null,
                occurredAt = occurredAt,
            )

        `when`("EventJson으로 직렬화하면") {
            val json = EventJson.encode(guestReservationCancelled())
            val normalized = json.replace(Regex("\\s+"), "")

            then("동결된 골든 JSON 문자열과 완전히 동일하다(필드명·키 순서·eventType 리터럴 동시 고정)") {
                normalized shouldBe guestGoldenJson
            }
        }

        `when`("직렬화 후 같은 타입으로 역직렬화하면") {
            val original = guestReservationCancelled()
            val json = EventJson.encode(original)
            val restored = EventJson.decode<ReservationCancelled>(json)

            then("원본 인스턴스와 완전히 동등하다") {
                restored shouldBe original
            }

            then("occurredAt의 나노초 정밀도가 손실 없이 복원된다") {
                restored.occurredAt shouldBe original.occurredAt
                restored.occurredAt.nano shouldBe original.occurredAt.nano
            }
        }

        `when`("nanoAdjustment가 0인 Instant로 왕복하면") {
            val original =
                guestReservationCancelled(occurredAt = Instant.parse("2024-03-15T09:30:45Z"))
            val restored = EventJson.decode<ReservationCancelled>(EventJson.encode(original))

            then("여전히 원본과 동등하다") {
                restored shouldBe original
            }
        }
    }

    given("OWNER가 취소한 ReservationCancelled 통합 이벤트가 주어졌을 때") {
        fun ownerReservationCancelled(occurredAt: Instant = fixedOccurredAt) =
            reservationCancelled(
                cancelledBy = CancelledBy.OWNER,
                reason = "매장사정으로인한임시휴무로예약을취소합니다긴급설비점검이필요합니다",
                occurredAt = occurredAt,
            )

        `when`("EventJson으로 직렬화하면") {
            val json = EventJson.encode(ownerReservationCancelled())
            val normalized = json.replace(Regex("\\s+"), "")

            then("동결된 골든 JSON 문자열과 완전히 동일하다(필드명·키 순서·eventType 리터럴 동시 고정)") {
                normalized shouldBe ownerGoldenJson
            }
        }

        `when`("직렬화 후 같은 타입으로 역직렬화하면") {
            val original = ownerReservationCancelled()
            val json = EventJson.encode(original)
            val restored = EventJson.decode<ReservationCancelled>(json)

            then("원본 인스턴스와 완전히 동등하다") {
                restored shouldBe original
            }

            then("occurredAt의 나노초 정밀도가 손실 없이 복원된다") {
                restored.occurredAt shouldBe original.occurredAt
                restored.occurredAt.nano shouldBe original.occurredAt.nano
            }
        }

        `when`("nanoAdjustment가 0인 Instant로 왕복하면") {
            val original =
                ownerReservationCancelled(occurredAt = Instant.parse("2024-03-15T09:30:45Z"))
            val restored = EventJson.decode<ReservationCancelled>(EventJson.encode(original))

            then("여전히 원본과 동등하다") {
                restored shouldBe original
            }
        }
    }
})
