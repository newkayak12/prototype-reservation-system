package com.reservation.persistence.reservation.repository.dsl

import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.reservation.entity.QReservationEntity.reservationEntity
import com.reservation.reservation.port.output.IsReservationExists
import com.reservation.reservation.port.output.IsReservationExists.IsReservationExistsInquiry
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils

@Component
class IsReservationExistsRepository(
    private val query: JPAQueryFactory,
) : IsReservationExists {
    override fun query(inquiry: IsReservationExistsInquiry): Boolean {
        return StringUtils.hasText(
            query.select(
                reservationEntity.identifier,
            )
                .from(reservationEntity)
                .where(
                    ReservationTimeTablaSpec.reservationTimeTableIdEqOrFalse(inquiry.timeTableId),
                    ReservationTimeTableOccupancySpec
                        .reservationTimeTableOccupancyIdEqOrFalse(inquiry.timeTableOccupancyId),
                )
                .fetchFirst(),
        )
    }
}
