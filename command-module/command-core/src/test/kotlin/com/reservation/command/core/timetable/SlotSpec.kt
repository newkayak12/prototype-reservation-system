package com.reservation.command.core.timetable

import com.reservation.command.core.support.DomainEvent
import com.reservation.command.core.timetable.command.BlockSlot
import com.reservation.command.core.timetable.command.ConfirmSeat
import com.reservation.command.core.timetable.command.HoldSeat
import com.reservation.command.core.timetable.command.ProvisionSlot
import com.reservation.command.core.timetable.command.ReleaseSeat
import com.reservation.command.core.timetable.command.UnblockSlot
import com.reservation.command.core.timetable.event.SeatConfirmed
import com.reservation.command.core.timetable.event.SeatHeld
import com.reservation.command.core.timetable.event.SeatReleased
import com.reservation.command.core.timetable.event.SlotBlocked
import com.reservation.command.core.timetable.event.SlotProvisioned
import com.reservation.command.core.timetable.event.SlotUnblocked
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private val DATE = LocalDate.of(2026, 8, 10)
private val DAY = DayOfWeek.MONDAY
private val START_TIME = LocalTime.of(18, 0)
private val END_TIME = LocalTime.of(19, 30)
private val HELD_AT = Instant.parse("2026-08-01T09:00:00Z")
private val HOLD_EXPIRES_AT = Instant.parse("2026-08-01T09:10:00Z")
private val CONFIRMED_AT = Instant.parse("2026-08-01T09:05:00Z")
private val RELEASED_AT = Instant.parse("2026-08-01T09:15:00Z")
private val BLOCKED_AT = Instant.parse("2026-08-01T08:00:00Z")
private val UNBLOCKED_AT = Instant.parse("2026-08-01T08:30:00Z")

private fun provisionSlot() =
    ProvisionSlot(
        slotId = "slot-1",
        restaurantId = "restaurant-1",
        date = DATE,
        day = DAY,
        startTime = START_TIME,
        endTime = END_TIME,
        tableNumber = 3,
        tableSize = 4,
    )

private fun slotProvisioned() =
    SlotProvisioned(
        slotId = "slot-1",
        restaurantId = "restaurant-1",
        date = DATE,
        day = DAY,
        startTime = START_TIME,
        endTime = END_TIME,
        tableNumber = 3,
        tableSize = 4,
    )

private fun availableSlot() = Slot.from(slotProvisioned())

private fun holdSeatCommand() =
    HoldSeat(
        reservationId = "reservation-1",
        userId = "user-1",
        heldAt = HELD_AT,
        holdExpiresAt = HOLD_EXPIRES_AT,
    )

private fun seatHeld() =
    SeatHeld(
        slotId = "slot-1",
        reservationId = "reservation-1",
        userId = "user-1",
        restaurantId = "restaurant-1",
        date = DATE,
        startTime = START_TIME,
        endTime = END_TIME,
        tableNumber = 3,
        heldAt = HELD_AT,
        holdExpiresAt = HOLD_EXPIRES_AT,
    )

private fun heldSlot() = availableSlot().apply(seatHeld())

class SlotSpec : BehaviorSpec({

    given("슬롯을 프로비저닝하는 커맨드가 주어졌을 때") {
        `when`("Slot.handle(ProvisionSlot)을 실행하면") {
            val events = Slot.handle(provisionSlot())

            then("SlotProvisioned 이벤트 1건을 방출한다") {
                events shouldBe listOf(slotProvisioned())
            }
        }

        `when`("Slot.from(SlotProvisioned)으로 최초 상태를 만들면") {
            val slot = availableSlot()

            then("AVAILABLE 상태의 슬롯이 만들어진다") {
                slot.slotId shouldBe "slot-1"
                slot.restaurantId shouldBe "restaurant-1"
                slot.date shouldBe DATE
                slot.day shouldBe DAY
                slot.startTime shouldBe START_TIME
                slot.endTime shouldBe END_TIME
                slot.tableNumber shouldBe 3
                slot.tableSize shouldBe 4
                slot.status shouldBe SlotStatus.AVAILABLE
            }
        }
    }

    given("AVAILABLE 슬롯이 주어졌을 때") {
        val slot = availableSlot()

        `when`("HoldSeat를 handle하면") {
            val events = slot.handle(holdSeatCommand())

            then("SeatHeld 이벤트 1건을 방출한다") {
                events shouldBe listOf(seatHeld())
            }
        }

        `when`("BlockSlot을 handle하면") {
            val command =
                BlockSlot(
                    blockedBy = "owner-1",
                    blockedAt = BLOCKED_AT,
                )
            val events = slot.handle(command)

            then("SlotBlocked 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        SlotBlocked(
                            slotId = "slot-1",
                            blockedBy = "owner-1",
                            blockedAt = BLOCKED_AT,
                        ),
                    )
            }
        }

        `when`("ConfirmSeat를 handle하면") {
            then("불변식 #4 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    slot.handle(
                        ConfirmSeat(
                            reservationId = "reservation-1",
                            confirmedAt = CONFIRMED_AT,
                        ),
                    )
                }
            }
        }

        `when`("SeatHeld를 apply하면") {
            val next = slot.apply(seatHeld())

            then("HELD 상태로 전이하고 점유 정보를 채운다") {
                next.status shouldBe SlotStatus.HELD
                next.reservationId shouldBe "reservation-1"
                next.userId shouldBe "user-1"
                next.heldAt shouldBe HELD_AT
                next.holdExpiresAt shouldBe HOLD_EXPIRES_AT
            }
        }

        `when`("SlotBlocked를 apply하면") {
            val event =
                SlotBlocked(
                    slotId = "slot-1",
                    blockedBy = "owner-1",
                    blockedAt = BLOCKED_AT,
                )
            val next = slot.apply(event)

            then("BLOCKED 상태로 전이하고 차단 정보를 채운다") {
                next.status shouldBe SlotStatus.BLOCKED
                next.blockedBy shouldBe "owner-1"
                next.blockedAt shouldBe BLOCKED_AT
            }
        }

        `when`("무관한 이벤트(SeatConfirmed)를 apply하면") {
            val next =
                slot.apply(
                    SeatConfirmed(
                        slotId = "slot-1",
                        reservationId = "reservation-1",
                        confirmedAt = CONFIRMED_AT,
                    ),
                )

            then("예외 없이 자기 자신을 그대로 반환한다") {
                next shouldBeSameInstanceAs slot
            }
        }
    }

    given("HELD 슬롯이 주어졌을 때") {
        val slot = heldSlot()

        `when`("HoldSeat를 handle하면") {
            then("불변식 #1 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    slot.handle(holdSeatCommand())
                }
            }
        }

        `when`("ConfirmSeat를 handle하면") {
            val command =
                ConfirmSeat(
                    reservationId = "reservation-1",
                    confirmedAt = CONFIRMED_AT,
                )
            val events = slot.handle(command)

            then("SeatConfirmed 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        SeatConfirmed(
                            slotId = "slot-1",
                            reservationId = "reservation-1",
                            confirmedAt = CONFIRMED_AT,
                        ),
                    )
            }
        }

        `when`("ReleaseSeat를 handle하면") {
            val command =
                ReleaseSeat(
                    reservationId = "reservation-1",
                    releasedAt = RELEASED_AT,
                )
            val events = slot.handle(command)

            then("SeatReleased 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        SeatReleased(
                            slotId = "slot-1",
                            reservationId = "reservation-1",
                            releasedAt = RELEASED_AT,
                        ),
                    )
            }
        }

        `when`("SeatConfirmed를 apply하면") {
            val next =
                slot.apply(
                    SeatConfirmed(
                        slotId = "slot-1",
                        reservationId = "reservation-1",
                        confirmedAt = CONFIRMED_AT,
                    ),
                )

            then("CONFIRMED 상태로 전이한다") {
                next.status shouldBe SlotStatus.CONFIRMED
                next.confirmedAt shouldBe CONFIRMED_AT
            }
        }

        `when`("SeatReleased를 apply하면") {
            val next =
                slot.apply(
                    SeatReleased(
                        slotId = "slot-1",
                        reservationId = "reservation-1",
                        releasedAt = RELEASED_AT,
                    ),
                )

            then("AVAILABLE 상태로 되돌아가고 점유 정보가 지워진다") {
                next.status shouldBe SlotStatus.AVAILABLE
                next.reservationId shouldBe null
                next.userId shouldBe null
                next.heldAt shouldBe null
                next.holdExpiresAt shouldBe null
            }
        }
    }

    given("CONFIRMED 슬롯이 주어졌을 때") {
        val slot =
            heldSlot()
                .apply(
                    SeatConfirmed(
                        slotId = "slot-1",
                        reservationId = "reservation-1",
                        confirmedAt = CONFIRMED_AT,
                    ),
                )

        `when`("ReleaseSeat를 handle하면") {
            val command =
                ReleaseSeat(
                    reservationId = "reservation-1",
                    releasedAt = RELEASED_AT,
                )
            val events = slot.handle(command)

            then("SeatReleased 이벤트 1건을 방출한다 (전이 #6)") {
                events shouldBe
                    listOf(
                        SeatReleased(
                            slotId = "slot-1",
                            reservationId = "reservation-1",
                            releasedAt = RELEASED_AT,
                        ),
                    )
            }
        }

        `when`("SeatReleased를 apply하면") {
            val next =
                slot
                    .apply(
                        SeatReleased(
                            slotId = "slot-1",
                            reservationId = "reservation-1",
                            releasedAt = RELEASED_AT,
                        ),
                    )

            then("AVAILABLE 상태로 되돌아간다") {
                next.status shouldBe SlotStatus.AVAILABLE
                next.confirmedAt shouldBe null
            }
        }
    }

    given("BLOCKED 슬롯이 주어졌을 때") {
        val slot =
            availableSlot().apply(
                SlotBlocked(
                    slotId = "slot-1",
                    blockedBy = "owner-1",
                    blockedAt = BLOCKED_AT,
                ),
            )

        `when`("HoldSeat를 handle하면") {
            then("불변식 #5 위반으로 거부된다") {
                shouldThrow<IllegalArgumentException> {
                    slot.handle(holdSeatCommand())
                }
            }
        }

        `when`("UnblockSlot을 handle하면") {
            val command =
                UnblockSlot(
                    unblockedBy = "owner-1",
                    unblockedAt = UNBLOCKED_AT,
                )
            val events = slot.handle(command)

            then("SlotUnblocked 이벤트 1건을 방출한다") {
                events shouldBe
                    listOf(
                        SlotUnblocked(
                            slotId = "slot-1",
                            unblockedBy = "owner-1",
                            unblockedAt = UNBLOCKED_AT,
                        ),
                    )
            }
        }

        `when`("SlotUnblocked를 apply하면") {
            val next =
                slot.apply(
                    SlotUnblocked(
                        slotId = "slot-1",
                        unblockedBy = "owner-1",
                        unblockedAt = UNBLOCKED_AT,
                    ),
                )

            then("AVAILABLE 상태로 되돌아가고 차단 정보가 지워진다") {
                next.status shouldBe SlotStatus.AVAILABLE
                next.blockedBy shouldBe null
                next.blockedAt shouldBe null
            }
        }
    }

    given("커맨드 체인으로 만든 상태와 이벤트 리하이드레이션 결과가 있을 때") {
        `when`("동일한 커맨드 시퀀스를 handle+apply로 직접 적용하고, 같은 이벤트를 from+fold로 리하이드레이션하면") {
            val provisioned = availableSlot()
            val heldEvent =
                provisioned.handle(holdSeatCommand()).single()
            val held = provisioned.apply(heldEvent)
            val confirmedEvent =
                held.handle(
                    ConfirmSeat(
                        reservationId = "reservation-1",
                        confirmedAt = CONFIRMED_AT,
                    ),
                ).single()
            val commandDriven = held.apply(confirmedEvent)

            val history: List<DomainEvent> =
                listOf(
                    seatHeld(),
                    SeatConfirmed(
                        slotId = "slot-1",
                        reservationId = "reservation-1",
                        confirmedAt = CONFIRMED_AT,
                    ),
                )
            val rehydrated =
                history.fold(Slot.from(slotProvisioned())) { state, event ->
                    state.apply(event)
                }

            then("두 상태는 완전히 동일하다") {
                rehydrated shouldBe commandDriven
                rehydrated.status shouldBe SlotStatus.CONFIRMED
            }
        }
    }
})
