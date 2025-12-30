package com.reservation.persistence.reservation.entity

import com.reservation.persistence.common.TimeBasedPrimaryKey
import com.reservation.persistence.reservation.entity.vo.ReservationBooker
import com.reservation.persistence.reservation.entity.vo.ReservationRestaurant
import com.reservation.persistence.reservation.entity.vo.ReservationTimeTable
import com.reservation.persistence.reservation.entity.vo.ReservationTimeTableOccupancy
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Table(
    catalog = "prototype_reservation",
    name = "reservation",
    indexes = [
        Index(
            columnList = "user_id, reservation_date, reservation_status",
            unique = false,
            name = "index_user_id_reservation_date_reservation_status",
        ),
    ],
)
@Entity
class ReservationEntity(
    @Embedded
    val booker: ReservationBooker,
    @Embedded
    val restaurant: ReservationRestaurant,
    @Embedded
    val timeTable: ReservationTimeTable,
    @Embedded
    val timeTableOccupancy: ReservationTimeTableOccupancy,
) : TimeBasedPrimaryKey()
