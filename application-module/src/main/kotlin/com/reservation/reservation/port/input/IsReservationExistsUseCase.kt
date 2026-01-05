package com.reservation.reservation.port.input

import com.reservation.reservation.port.input.query.IsReservationExistsQuery

interface IsReservationExistsUseCase {
    fun execute(query: IsReservationExistsQuery): Boolean
}
