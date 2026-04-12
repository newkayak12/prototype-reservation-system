package com.reservation.persistence.reservation.repository.adapter

import com.reservation.persistence.reservation.entity.ReservationEntity
import com.reservation.persistence.reservation.entity.vo.ReservationBooker
import com.reservation.persistence.reservation.entity.vo.ReservationRestaurant
import com.reservation.persistence.reservation.entity.vo.ReservationTimeTable
import com.reservation.persistence.reservation.entity.vo.ReservationTimeTableInformation
import com.reservation.persistence.reservation.entity.vo.ReservationTimeTableOccupancy
import com.reservation.persistence.reservation.entity.vo.ReservationTimeTableSchedule
import com.reservation.persistence.reservation.repository.jpa.ReservationJpaRepository
import com.reservation.reservation.port.output.CreateReservation
import com.reservation.reservation.port.output.CreateReservation.CreateReservationInquiry
import org.springframework.stereotype.Component

@Component
class CreateReservationAdapter(
    private val jpaRepository: ReservationJpaRepository,
) : CreateReservation {
    override fun command(inquiry: CreateReservationInquiry): Boolean {
        val entity = inquiry.toEntity()
        val saved = jpaRepository.save(entity)

        return saved.id != null
    }

    private fun CreateReservationInquiry.toEntity() =
        ReservationEntity(
            booker = ReservationBooker(userId = this.userId),
            restaurant = ReservationRestaurant(restaurantId = this.restaurantId),
            timeTable =
                ReservationTimeTable(
                    timeTableId = this.timeTableId,
                    schedule =
                        ReservationTimeTableSchedule(
                            reservationDate = this.date,
                            reservationDay = this.day,
                            reservationTime = this.startTime,
                        ),
                    tableInformation =
                        ReservationTimeTableInformation(
                            reservationSeatSize = this.tableSize,
                        ),
                ),
            timeTableOccupancy =
                ReservationTimeTableOccupancy(
                    timetableOccupancyId = this.timeTableOccupancyId,
                    reservationStatus = this.reservationStatus,
                    reservationOccupiedDatetime = this.occupiedDatetime,
                ),
        )
}
