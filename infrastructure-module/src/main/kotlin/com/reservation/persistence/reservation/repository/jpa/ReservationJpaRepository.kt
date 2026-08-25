package com.reservation.persistence.reservation.repository.jpa

import com.reservation.persistence.reservation.entity.ReservationEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface ReservationJpaRepository : CrudRepository<ReservationEntity, String> {
    companion object {
        private const val EXISTS_RESERVATION_SQL = """
        SELECT CASE WHEN COUNT(reservation) > 0 THEN true ELSE false END
        FROM ReservationEntity reservation
        WHERE reservation.timeTable.timeTableId = :timeTableId
        AND reservation.timeTableOccupancy.timetableOccupancyId = :timeTableOccupancyId
        """
    }

    @Query(EXISTS_RESERVATION_SQL)
    fun existsReservation(
        timeTableId: String,
        timeTableOccupancyId: String,
    ): Boolean
}
