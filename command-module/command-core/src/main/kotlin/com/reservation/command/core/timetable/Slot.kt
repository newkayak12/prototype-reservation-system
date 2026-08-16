package com.reservation.command.core.timetable

import com.reservation.command.core.support.Command
import com.reservation.command.core.support.DomainEvent
import com.reservation.command.core.support.EventSourcingAggregate
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Suppress("LongParameterList")
data class Slot private constructor(
    val slotId: String,
    val restaurantId: String,
    val date: LocalDate,
    val day: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val tableNumber: Int,
    val tableSize: Int,
    val status: SlotStatus,
    val reservationId: String?,
    val userId: String?,
    val heldAt: Instant?,
    val holdExpiresAt: Instant?,
    val confirmedAt: Instant?,
    val blockedBy: String?,
    val blockedAt: Instant?,
) : EventSourcingAggregate<Slot>() {
    override fun handle(command: Command): List<DomainEvent> =
        when (command) {
            is HoldSeat -> handleHoldSeat(command)
            is BlockSlot -> handleBlockSlot(command)
            is ConfirmSeat -> handleConfirmSeat(command)
            is ReleaseSeat -> handleReleaseSeat(command)
            is UnblockSlot -> handleUnblockSlot(command)
            else -> error("Slot이 처리할 수 없는 커맨드: $command")
        }

    override fun apply(event: DomainEvent): Slot =
        when (event) {
            is SeatHeld -> applySeatHeld(event)
            is SeatConfirmed -> applySeatConfirmed(event)
            is SeatReleased -> applySeatReleased(event)
            is SlotBlocked -> applySlotBlocked(event)
            is SlotUnblocked -> applySlotUnblocked(event)
            else -> this
        }

    private fun handleHoldSeat(command: HoldSeat): List<DomainEvent> {
        require(status == SlotStatus.AVAILABLE) { "AVAILABLE 상태의 슬롯만 점유할 수 있다" }
        return listOf(
            SeatHeld(
                slotId = slotId,
                reservationId = command.reservationId,
                userId = command.userId,
                restaurantId = restaurantId,
                date = date,
                startTime = startTime,
                endTime = endTime,
                tableNumber = tableNumber,
                heldAt = command.heldAt,
                holdExpiresAt = command.holdExpiresAt,
            ),
        )
    }

    private fun handleBlockSlot(command: BlockSlot): List<DomainEvent> =
        listOf(
            SlotBlocked(
                slotId = slotId,
                blockedBy = command.blockedBy,
                blockedAt = command.blockedAt,
            ),
        )

    private fun handleConfirmSeat(command: ConfirmSeat): List<DomainEvent> {
        require(status == SlotStatus.HELD) { "HELD 상태의 슬롯만 확정할 수 있다" }
        return listOf(
            SeatConfirmed(
                slotId = slotId,
                reservationId = command.reservationId,
                confirmedAt = command.confirmedAt,
            ),
        )
    }

    private fun handleReleaseSeat(command: ReleaseSeat): List<DomainEvent> =
        listOf(
            SeatReleased(
                slotId = slotId,
                reservationId = command.reservationId,
                releasedAt = command.releasedAt,
            ),
        )

    private fun handleUnblockSlot(command: UnblockSlot): List<DomainEvent> =
        listOf(
            SlotUnblocked(
                slotId = slotId,
                unblockedBy = command.unblockedBy,
                unblockedAt = command.unblockedAt,
            ),
        )

    private fun applySeatHeld(event: SeatHeld): Slot =
        copy(
            status = SlotStatus.HELD,
            reservationId = event.reservationId,
            userId = event.userId,
            heldAt = event.heldAt,
            holdExpiresAt = event.holdExpiresAt,
        )

    private fun applySeatConfirmed(event: SeatConfirmed): Slot =
        copy(
            status = SlotStatus.CONFIRMED,
            reservationId = event.reservationId,
            confirmedAt = event.confirmedAt,
        )

    private fun applySeatReleased(ignored: SeatReleased): Slot =
        copy(
            status = SlotStatus.AVAILABLE,
            reservationId = null,
            userId = null,
            heldAt = null,
            holdExpiresAt = null,
            confirmedAt = null,
        )

    private fun applySlotBlocked(event: SlotBlocked): Slot =
        copy(status = SlotStatus.BLOCKED, blockedBy = event.blockedBy, blockedAt = event.blockedAt)

    private fun applySlotUnblocked(ignored: SlotUnblocked): Slot =
        copy(status = SlotStatus.AVAILABLE, blockedBy = null, blockedAt = null)

    companion object {
        fun handle(command: ProvisionSlot): List<DomainEvent> =
            listOf(
                SlotProvisioned(
                    slotId = command.slotId,
                    restaurantId = command.restaurantId,
                    date = command.date,
                    day = command.day,
                    startTime = command.startTime,
                    endTime = command.endTime,
                    tableNumber = command.tableNumber,
                    tableSize = command.tableSize,
                ),
            )

        fun from(event: SlotProvisioned): Slot =
            Slot(
                slotId = event.slotId,
                restaurantId = event.restaurantId,
                date = event.date,
                day = event.day,
                startTime = event.startTime,
                endTime = event.endTime,
                tableNumber = event.tableNumber,
                tableSize = event.tableSize,
                status = SlotStatus.AVAILABLE,
                reservationId = null,
                userId = null,
                heldAt = null,
                holdExpiresAt = null,
                confirmedAt = null,
                blockedBy = null,
                blockedAt = null,
            )
    }
}
