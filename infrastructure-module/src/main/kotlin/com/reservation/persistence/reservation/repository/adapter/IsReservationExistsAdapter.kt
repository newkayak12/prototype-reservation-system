package com.reservation.persistence.reservation.repository.adapter

import com.reservation.persistence.reservation.repository.jpa.ReservationJpaRepository
import com.reservation.reservation.port.output.IsReservationExists
import com.reservation.reservation.port.output.IsReservationExists.IsReservationExistsInquiry
import org.springframework.stereotype.Component

@Component
class IsReservationExistsAdapter(
    private val jpaRepository: ReservationJpaRepository,
) : IsReservationExists {
    override fun query(inquiry: IsReservationExistsInquiry): Boolean =
        jpaRepository.existsReservation(inquiry.timeTableId, inquiry.timeTableOccupancyId)
}
