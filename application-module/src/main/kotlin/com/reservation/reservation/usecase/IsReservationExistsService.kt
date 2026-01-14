package com.reservation.reservation.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.reservation.port.input.IsReservationExistsUseCase
import com.reservation.reservation.port.input.query.IsReservationExistsQuery
import com.reservation.reservation.port.output.IsReservationExists
import com.reservation.reservation.port.output.IsReservationExists.IsReservationExistsInquiry

@UseCase
class IsReservationExistsService(
    val isReservationExists: IsReservationExists,
) : IsReservationExistsUseCase {
    override fun execute(query: IsReservationExistsQuery) =
        isReservationExists.query(query.toInquiry())

    private fun IsReservationExistsQuery.toInquiry() =
        IsReservationExistsInquiry(
            timeTableId = timeTableId,
            timeTableOccupancyId = timeTableOccupancyId,
        )
}
