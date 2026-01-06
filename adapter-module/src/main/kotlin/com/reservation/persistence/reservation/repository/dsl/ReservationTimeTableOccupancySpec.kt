package com.reservation.persistence.reservation.repository.dsl

import com.querydsl.core.types.dsl.Expressions
import com.reservation.persistence.reservation.entity.QReservationEntity.reservationEntity

object ReservationTimeTableOccupancySpec {
    fun reservationTimeTableOccupancyIdEqOrFalse(timeTableOccupancyId: String?) =
        timeTableOccupancyId?.let {
            reservationEntity.timeTableOccupancy.timetableOccupancyId.eq(it)
        }
            ?: Expressions.FALSE
}
