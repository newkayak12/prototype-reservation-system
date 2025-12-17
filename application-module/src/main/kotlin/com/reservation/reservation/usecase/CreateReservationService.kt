package com.reservation.reservation.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.reservation.policy.format.CreateReservationForm
import com.reservation.reservation.port.input.CreateReservationUseCase
import com.reservation.reservation.port.input.command.CreateReservationCommand
import com.reservation.reservation.port.output.CreateReservation
import com.reservation.reservation.port.output.CreateReservation.CreateReservationInquiry
import com.reservation.reservation.service.CreateReservationDomainService
import com.reservation.reservation.snapshot.ReservationSnapshot
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateReservationService(
    private val createReservationDomainService: CreateReservationDomainService,
    private val createReservation: CreateReservation,
) : CreateReservationUseCase {
    @Transactional
    override fun execute(command: CreateReservationCommand): Boolean {
        val snapshot = createReservationDomainService.createReservation(command.toForm())

        val inquiry = snapshot.toInquiry()
        return createReservation.command(inquiry)
    }

    private fun CreateReservationCommand.toForm() =
        CreateReservationForm(
            restaurantId = restaurantId,
            userId = userId,
            timeTableId = timeTableId,
            timeTableOccupancyId = timeTableOccupancyId,
            date = date,
            day = day,
            startTime = startTime,
            endTime = endTime,
            tableNumber = tableNumber,
            tableSize = tableSize,
            occupiedDatetime = occupiedDatetime,
        )

    private fun ReservationSnapshot.toInquiry() =
        CreateReservationInquiry(
            restaurantId = restaurantInformation.restaurantId,
            userId = booker.userId,
            timeTableId = schedule.timeTableId,
            timeTableOccupancyId = occupancy.timeTableOccupancyId,
            date = schedule.date,
            day = schedule.day,
            startTime = schedule.startTime,
            endTime = schedule.endTime,
            tableNumber = restaurantInformation.tableNumber,
            tableSize = restaurantInformation.tableSize,
            occupiedDatetime = occupancy.occupiedDatetime,
            reservationStatus = reservationStatus,
        )
}
