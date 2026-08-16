package com.reservation.command.core.reservation

import com.reservation.command.core.reservation.command.AutoConfirmVisit
import com.reservation.command.core.reservation.command.CancelReservation
import com.reservation.command.core.reservation.command.ConfirmReservation
import com.reservation.command.core.reservation.command.ConfirmVisit
import com.reservation.command.core.reservation.command.CreateReservation
import com.reservation.command.core.reservation.command.ExpireReservation
import com.reservation.command.core.reservation.command.FailReservation
import com.reservation.command.core.reservation.command.JudgeNoShow
import com.reservation.command.core.reservation.event.RefundRequired
import com.reservation.command.core.reservation.event.ReservationCancelled
import com.reservation.command.core.reservation.event.ReservationConfirmed
import com.reservation.command.core.reservation.event.ReservationCreated
import com.reservation.command.core.reservation.event.ReservationExpired
import com.reservation.command.core.reservation.event.ReservationFailed
import com.reservation.command.core.reservation.event.ReservationNoShow
import com.reservation.command.core.reservation.event.VisitConfirmed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val RESERVATION_ID = "0188f2d3-0000-7000-8000-000000000001"
private const val USER_ID = "0188f2d3-0000-7000-8000-000000000002"
private const val RESTAURANT_ID = "0188f2d3-0000-7000-8000-000000000003"
private const val SLOT_ID = "0188f2d3-0000-7000-8000-000000000004"
private const val OTHER_USER_ID = "0188f2d3-0000-7000-8000-000000000099"

private val DATE = LocalDate.of(2026, 8, 20)
private val DAY = DayOfWeek.THURSDAY
private val START_TIME = LocalTime.of(18, 0)
private val END_TIME = LocalTime.of(19, 30)
private const val TABLE_NUMBER = 3
private const val TABLE_SIZE = 4

private val REQUESTED_AT = LocalDateTime.of(2026, 8, 1, 10, 0)
private val CONFIRMED_AT = LocalDateTime.of(2026, 8, 2, 9, 0)
private val FAILED_AT = LocalDateTime.of(2026, 8, 2, 9, 0)
private val EXPIRED_AT = LocalDateTime.of(2026, 8, 5, 0, 0)
private val REJECTED_AT = LocalDateTime.of(2026, 8, 6, 9, 0)
private val GUEST_CANCEL_AT_VALID = LocalDateTime.of(2026, 8, 10, 9, 0)
private val GUEST_CANCEL_AT_TOO_LATE = LocalDateTime.of(2026, 8, 19, 9, 0)
private val OWNER_CANCEL_AT_VALID = LocalDateTime.of(2026, 8, 20, 17, 0)
private val OWNER_CANCEL_AT_TOO_LATE = LocalDateTime.of(2026, 8, 21, 0, 0)
private val JUDGED_AT_VALID = LocalDateTime.of(2026, 8, 20, 18, 1)
private val JUDGED_AT_TOO_EARLY = LocalDateTime.of(2026, 8, 20, 18, 0)
private val VISIT_CONFIRMED_AT_VALID = LocalDateTime.of(2026, 8, 20, 18, 0)
private val VISIT_CONFIRMED_AT_TOO_EARLY = LocalDateTime.of(2026, 8, 20, 17, 59)
private val AUTO_VISIT_CONFIRMED_AT_VALID = LocalDateTime.of(2026, 8, 27, 18, 0)
private val AUTO_VISIT_CONFIRMED_AT_TOO_EARLY = LocalDateTime.of(2026, 8, 27, 17, 59)
private val VALID_OWNER_REASON = "a".repeat(30)

@Suppress("LongParameterList")
private fun createReservationCommand(
    reservationId: String = RESERVATION_ID,
    userId: String = USER_ID,
    restaurantId: String = RESTAURANT_ID,
    slotId: String = SLOT_ID,
    date: LocalDate = DATE,
    day: DayOfWeek = DAY,
    startTime: LocalTime = START_TIME,
    endTime: LocalTime = END_TIME,
    tableNumber: Int = TABLE_NUMBER,
    tableSize: Int = TABLE_SIZE,
    requestedAt: LocalDateTime = REQUESTED_AT,
) = CreateReservation(
    reservationId = reservationId,
    userId = userId,
    restaurantId = restaurantId,
    tableNumber = tableNumber,
    tableSize = tableSize,
    slotId = slotId,
    date = date,
    day = day,
    startTime = startTime,
    endTime = endTime,
    requestedAt = requestedAt,
)

private fun reservationCreated() =
    ReservationCreated(
        reservationId = RESERVATION_ID,
        userId = USER_ID,
        restaurantId = RESTAURANT_ID,
        tableNumber = TABLE_NUMBER,
        tableSize = TABLE_SIZE,
        slotId = SLOT_ID,
        date = DATE,
        day = DAY,
        startTime = START_TIME,
        endTime = END_TIME,
        requestedAt = REQUESTED_AT,
    )

private fun pendingReservation() = Reservation.from(reservationCreated())

private fun confirmedReservation() =
    pendingReservation().apply(ReservationConfirmed(RESERVATION_ID, CONFIRMED_AT))

private fun failedReservation() =
    pendingReservation().apply(ReservationFailed(RESERVATION_ID, FAILED_AT))

private fun expiredReservation() =
    pendingReservation().apply(ReservationExpired(RESERVATION_ID, EXPIRED_AT))

private fun cancelledReservation() =
    confirmedReservation()
        .apply(ReservationCancelled(RESERVATION_ID, CancelActor.GUEST, null, GUEST_CANCEL_AT_VALID))

private fun noShowReservation() =
    confirmedReservation().apply(ReservationNoShow(RESERVATION_ID, JUDGED_AT_VALID))

private fun visitedReservation() =
    confirmedReservation()
        .apply(VisitConfirmed(RESERVATION_ID, VisitConfirmer.OWNER, VISIT_CONFIRMED_AT_VALID))

class ReservationSpec : BehaviorSpec({

    given("Reservation.handle(CreateReservation) 제네시스 커맨드가 주어졌을 때") {
        `when`("실행하면") {
            val events = Reservation.handle(createReservationCommand())

            then("ReservationCreated 이벤트 1건을 방출한다") {
                events shouldBe listOf(reservationCreated())
            }
        }

        `when`("Reservation.from(ReservationCreated)으로 최초 상태를 만들면") {
            val reservation = pendingReservation()

            then("PENDING 상태로 21개 필드를 채운 예약이 만들어진다") {
                reservation.reservationId shouldBe RESERVATION_ID
                reservation.userId shouldBe USER_ID
                reservation.restaurantId shouldBe RESTAURANT_ID
                reservation.tableNumber shouldBe TABLE_NUMBER
                reservation.tableSize shouldBe TABLE_SIZE
                reservation.slotId shouldBe SLOT_ID
                reservation.date shouldBe DATE
                reservation.day shouldBe DAY
                reservation.startTime shouldBe START_TIME
                reservation.endTime shouldBe END_TIME
                reservation.status shouldBe ReservationStatus.PENDING
                reservation.requestedAt shouldBe REQUESTED_AT
                reservation.confirmedAt shouldBe null
                reservation.failedAt shouldBe null
                reservation.expiredAt shouldBe null
                reservation.cancelledBy shouldBe null
                reservation.cancelReason shouldBe null
                reservation.cancelledAt shouldBe null
                reservation.judgedAt shouldBe null
                reservation.visitConfirmedBy shouldBe null
                reservation.visitConfirmedAt shouldBe null
            }
        }

        `when`("예약 날짜가 미래가 아니면(불변식 #1)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Reservation.handle(createReservationCommand(date = REQUESTED_AT.toLocalDate()))
                }
            }
        }

        `when`("startTime이 endTime보다 늦으면(불변식 #2)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Reservation.handle(
                        createReservationCommand(startTime = END_TIME, endTime = START_TIME),
                    )
                }
            }
        }

        `when`("date의 요일과 day가 다르면(불변식 #3)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Reservation.handle(createReservationCommand(day = DayOfWeek.MONDAY))
                }
            }
        }

        `when`("tableNumber가 1 미만이면(불변식 #4)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Reservation.handle(createReservationCommand(tableNumber = 0))
                }
            }
        }

        `when`("tableSize가 1 미만이면(불변식 #5)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Reservation.handle(createReservationCommand(tableSize = 0))
                }
            }
        }

        `when`("reservationId가 UUID 포맷이 아니면(불변식 #6)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    Reservation.handle(createReservationCommand(reservationId = "not-a-uuid"))
                }
            }
        }
    }

    given("PENDING 예약이 주어졌을 때") {
        val reservation = pendingReservation()

        `when`("ConfirmReservation을 handle하면") {
            val events = reservation.handle(ConfirmReservation(CONFIRMED_AT))

            then("ReservationConfirmed 이벤트 1건을 방출한다") {
                events shouldBe listOf(ReservationConfirmed(RESERVATION_ID, CONFIRMED_AT))
            }
        }

        `when`("FailReservation을 handle하면") {
            val events = reservation.handle(FailReservation(FAILED_AT))

            then("ReservationFailed 이벤트 1건을 방출한다") {
                events shouldBe listOf(ReservationFailed(RESERVATION_ID, FAILED_AT))
            }
        }

        `when`("ExpireReservation을 handle하면") {
            val events = reservation.handle(ExpireReservation(EXPIRED_AT))

            then("ReservationExpired 이벤트 1건을 방출한다") {
                events shouldBe listOf(ReservationExpired(RESERVATION_ID, EXPIRED_AT))
            }
        }

        `when`("CancelReservation(GUEST, 3일 이전)을 handle하면") {
            val command =
                CancelReservation(
                    cancelledBy = CancelActor.GUEST,
                    requesterId = USER_ID,
                    reason = null,
                    cancelledAt = GUEST_CANCEL_AT_VALID,
                )
            val events = reservation.handle(command)

            then("ReservationCancelled 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        ReservationCancelled(
                            RESERVATION_ID,
                            CancelActor.GUEST,
                            null,
                            GUEST_CANCEL_AT_VALID,
                        ),
                    )
            }
        }

        `when`("JudgeNoShow를 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(JudgeNoShow(JUDGED_AT_VALID))
                }
            }
        }

        `when`("ConfirmVisit을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(ConfirmVisit(VISIT_CONFIRMED_AT_VALID))
                }
            }
        }

        `when`("ReservationConfirmed를 apply하면") {
            val next = reservation.apply(ReservationConfirmed(RESERVATION_ID, CONFIRMED_AT))

            then("CONFIRMED 상태로 전이하고 confirmedAt을 채운다") {
                next.status shouldBe ReservationStatus.CONFIRMED
                next.confirmedAt shouldBe CONFIRMED_AT
            }
        }

        `when`("ReservationFailed를 apply하면") {
            val next = reservation.apply(ReservationFailed(RESERVATION_ID, FAILED_AT))

            then("FAILED 상태로 전이하고 failedAt을 채운다") {
                next.status shouldBe ReservationStatus.FAILED
                next.failedAt shouldBe FAILED_AT
            }
        }

        `when`("ReservationExpired를 apply하면") {
            val next = reservation.apply(ReservationExpired(RESERVATION_ID, EXPIRED_AT))

            then("EXPIRED 상태로 전이하고 expiredAt을 채운다") {
                next.status shouldBe ReservationStatus.EXPIRED
                next.expiredAt shouldBe EXPIRED_AT
            }
        }

        `when`("ReservationCancelled(GUEST)를 apply하면") {
            val event =
                ReservationCancelled(RESERVATION_ID, CancelActor.GUEST, null, GUEST_CANCEL_AT_VALID)
            val next = reservation.apply(event)

            then("CANCELLED 상태로 전이하고 취소 정보를 채운다") {
                next.status shouldBe ReservationStatus.CANCELLED
                next.cancelledBy shouldBe CancelActor.GUEST
                next.cancelReason shouldBe null
                next.cancelledAt shouldBe GUEST_CANCEL_AT_VALID
            }
        }
    }

    given("CONFIRMED 예약이 주어졌을 때") {
        val reservation = confirmedReservation()

        `when`("CancelReservation(GUEST, 3일 이전)을 handle하면") {
            val command =
                CancelReservation(
                    cancelledBy = CancelActor.GUEST,
                    requesterId = USER_ID,
                    reason = null,
                    cancelledAt = GUEST_CANCEL_AT_VALID,
                )
            val events = reservation.handle(command)

            then("ReservationCancelled 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        ReservationCancelled(
                            RESERVATION_ID,
                            CancelActor.GUEST,
                            null,
                            GUEST_CANCEL_AT_VALID,
                        ),
                    )
            }
        }

        `when`("CancelReservation(OWNER, 방문일시 이전, 사유 유효)을 handle하면") {
            val command =
                CancelReservation(
                    cancelledBy = CancelActor.OWNER,
                    requesterId = RESTAURANT_ID,
                    reason = VALID_OWNER_REASON,
                    cancelledAt = OWNER_CANCEL_AT_VALID,
                )
            val events = reservation.handle(command)

            then("ReservationCancelled 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        ReservationCancelled(
                            RESERVATION_ID,
                            CancelActor.OWNER,
                            VALID_OWNER_REASON,
                            OWNER_CANCEL_AT_VALID,
                        ),
                    )
            }
        }

        `when`("JudgeNoShow(예약 시각 경과 후)를 handle하면") {
            val events = reservation.handle(JudgeNoShow(JUDGED_AT_VALID))

            then("ReservationNoShow 이벤트 1건을 방출한다") {
                events shouldBe listOf(ReservationNoShow(RESERVATION_ID, JUDGED_AT_VALID))
            }
        }

        `when`("ConfirmVisit(예약 시각 이후)을 handle하면") {
            val events = reservation.handle(ConfirmVisit(VISIT_CONFIRMED_AT_VALID))

            then("VisitConfirmed(OWNER) 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        VisitConfirmed(
                            RESERVATION_ID,
                            VisitConfirmer.OWNER,
                            VISIT_CONFIRMED_AT_VALID,
                        ),
                    )
            }
        }

        `when`("AutoConfirmVisit(7일 경과 후)을 handle하면") {
            val events = reservation.handle(AutoConfirmVisit(AUTO_VISIT_CONFIRMED_AT_VALID))

            then("VisitConfirmed(SYSTEM) 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        VisitConfirmed(
                            RESERVATION_ID,
                            VisitConfirmer.SYSTEM,
                            AUTO_VISIT_CONFIRMED_AT_VALID,
                        ),
                    )
            }
        }

        `when`("FailReservation을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(FailReservation(FAILED_AT))
                }
            }
        }

        `when`("ExpireReservation을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(ExpireReservation(EXPIRED_AT))
                }
            }
        }

        `when`("손님 취소가 방문 3일 이내면(불변식 #7)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(
                        CancelReservation(
                            cancelledBy = CancelActor.GUEST,
                            requesterId = USER_ID,
                            reason = null,
                            cancelledAt = GUEST_CANCEL_AT_TOO_LATE,
                        ),
                    )
                }
            }
        }

        `when`("점주 취소가 방문일시 이후면(불변식 #8)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(
                        CancelReservation(
                            cancelledBy = CancelActor.OWNER,
                            requesterId = RESTAURANT_ID,
                            reason = VALID_OWNER_REASON,
                            cancelledAt = OWNER_CANCEL_AT_TOO_LATE,
                        ),
                    )
                }
            }
        }

        `when`("점주 취소에 사유가 없으면(불변식 #9)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(
                        CancelReservation(
                            cancelledBy = CancelActor.OWNER,
                            requesterId = RESTAURANT_ID,
                            reason = null,
                            cancelledAt = OWNER_CANCEL_AT_VALID,
                        ),
                    )
                }
            }
        }

        `when`("손님 본인이 아닌 사람이 취소를 요청하면(불변식 #10 예약자 본인 절반)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(
                        CancelReservation(
                            cancelledBy = CancelActor.GUEST,
                            requesterId = OTHER_USER_ID,
                            reason = null,
                            cancelledAt = GUEST_CANCEL_AT_VALID,
                        ),
                    )
                }
            }
        }

        `when`("ConfirmVisit이 예약 시각 이전이면(불변식 #12)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(ConfirmVisit(VISIT_CONFIRMED_AT_TOO_EARLY))
                }
            }
        }

        `when`("AutoConfirmVisit이 7일 경과 이전이면(불변식 #13)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(AutoConfirmVisit(AUTO_VISIT_CONFIRMED_AT_TOO_EARLY))
                }
            }
        }

        `when`("JudgeNoShow가 예약 시각 이전/시각이면(불변식 #14)") {
            then("IllegalArgumentException을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(JudgeNoShow(JUDGED_AT_TOO_EARLY))
                }
            }
        }

        `when`("ReservationCancelled(OWNER)를 apply하면") {
            val event =
                ReservationCancelled(
                    RESERVATION_ID,
                    CancelActor.OWNER,
                    VALID_OWNER_REASON,
                    OWNER_CANCEL_AT_VALID,
                )
            val next = reservation.apply(event)

            then("CANCELLED 상태로 전이하고 취소 정보를 채운다") {
                next.status shouldBe ReservationStatus.CANCELLED
                next.cancelledBy shouldBe CancelActor.OWNER
                next.cancelReason shouldBe VALID_OWNER_REASON
                next.cancelledAt shouldBe OWNER_CANCEL_AT_VALID
            }
        }

        `when`("ReservationNoShow를 apply하면") {
            val next = reservation.apply(ReservationNoShow(RESERVATION_ID, JUDGED_AT_VALID))

            then("NO_SHOW 상태로 전이하고 judgedAt을 채운다") {
                next.status shouldBe ReservationStatus.NO_SHOW
                next.judgedAt shouldBe JUDGED_AT_VALID
            }
        }

        `when`("VisitConfirmed(OWNER)를 apply하면") {
            val event =
                VisitConfirmed(RESERVATION_ID, VisitConfirmer.OWNER, VISIT_CONFIRMED_AT_VALID)
            val next = reservation.apply(event)

            then("VISITED 상태로 전이하고 방문 확정 정보를 채운다") {
                next.status shouldBe ReservationStatus.VISITED
                next.visitConfirmedBy shouldBe VisitConfirmer.OWNER
                next.visitConfirmedAt shouldBe VISIT_CONFIRMED_AT_VALID
            }
        }

        `when`("VisitConfirmed(SYSTEM)를 apply하면") {
            val event =
                VisitConfirmed(RESERVATION_ID, VisitConfirmer.SYSTEM, AUTO_VISIT_CONFIRMED_AT_VALID)
            val next = reservation.apply(event)

            then("VISITED 상태로 전이하고 방문 확정 정보를 채운다") {
                next.status shouldBe ReservationStatus.VISITED
                next.visitConfirmedBy shouldBe VisitConfirmer.SYSTEM
                next.visitConfirmedAt shouldBe AUTO_VISIT_CONFIRMED_AT_VALID
            }
        }
    }

    given("EXPIRED 예약이 주어졌을 때") {
        val reservation = expiredReservation()

        `when`("지연된 ConfirmReservation을 handle하면") {
            val events = reservation.handle(ConfirmReservation(REJECTED_AT))

            then("확정을 거부하고 RefundRequired 이벤트 1건을 방출한다") {
                events shouldBe listOf(RefundRequired(RESERVATION_ID, REJECTED_AT))
            }

            then("RefundRequired를 apply해도 상태가 바뀌지 않는다") {
                val next = reservation.apply(RefundRequired(RESERVATION_ID, REJECTED_AT))
                next shouldBeSameInstanceAs reservation
            }
        }

        `when`("CancelReservation을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(
                        CancelReservation(
                            cancelledBy = CancelActor.GUEST,
                            requesterId = USER_ID,
                            reason = null,
                            cancelledAt = GUEST_CANCEL_AT_VALID,
                        ),
                    )
                }
            }
        }
    }

    given("FAILED 예약이 주어졌을 때") {
        val reservation = failedReservation()

        `when`("CancelReservation을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(
                        CancelReservation(
                            cancelledBy = CancelActor.GUEST,
                            requesterId = USER_ID,
                            reason = null,
                            cancelledAt = GUEST_CANCEL_AT_VALID,
                        ),
                    )
                }
            }
        }
    }

    given("CANCELLED 예약이 주어졌을 때") {
        val reservation = cancelledReservation()

        `when`("AutoConfirmVisit을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(AutoConfirmVisit(AUTO_VISIT_CONFIRMED_AT_VALID))
                }
            }
        }

        `when`("ConfirmReservation을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(ConfirmReservation(CONFIRMED_AT))
                }
            }
        }
    }

    given("NO_SHOW 예약이 주어졌을 때") {
        val reservation = noShowReservation()

        `when`("CancelReservation을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(
                        CancelReservation(
                            cancelledBy = CancelActor.OWNER,
                            requesterId = RESTAURANT_ID,
                            reason = VALID_OWNER_REASON,
                            cancelledAt = OWNER_CANCEL_AT_VALID,
                        ),
                    )
                }
            }
        }
    }

    given("VISITED 예약이 주어졌을 때") {
        val reservation = visitedReservation()

        `when`("ConfirmVisit을 handle하면") {
            then("불변식 #15 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    reservation.handle(ConfirmVisit(VISIT_CONFIRMED_AT_VALID))
                }
            }
        }
    }
})
