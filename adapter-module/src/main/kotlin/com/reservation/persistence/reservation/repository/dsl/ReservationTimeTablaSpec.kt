package com.reservation.persistence.reservation.repository.dsl

import com.querydsl.core.types.dsl.Expressions
import com.reservation.persistence.reservation.entity.QReservationEntity.reservationEntity

object ReservationTimeTablaSpec {
    fun reservationTimeTableIdEqOrFalse(timeTableId: String?) =
        timeTableId?.let {
            reservationEntity.timeTable.timeTableId.eq(it)
        }
            ?: Expressions.FALSE
}
