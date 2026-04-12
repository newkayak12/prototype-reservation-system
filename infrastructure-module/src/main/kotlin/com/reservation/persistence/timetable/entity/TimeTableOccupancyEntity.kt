package com.reservation.persistence.timetable.entity

import com.reservation.enumeration.OccupyStatus
import com.reservation.enumeration.OccupyStatus.OCCUPIED
import com.reservation.enumeration.OccupyStatus.UNOCCUPIED
import com.reservation.persistence.common.TimeBasedPrimaryKey
import jakarta.persistence.Column
import jakarta.persistence.ConstraintMode.NO_CONSTRAINT
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType.EAGER
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

@Table(
    catalog = "prototype_reservation",
    name = "timetable_occupancy",
    indexes = [],
)
@Entity
@DynamicUpdate
class TimeTableOccupancyEntity(
    @JoinColumn(
        name = "timetable_id",
        foreignKey = ForeignKey(NO_CONSTRAINT),
    )
    @ManyToOne(fetch = EAGER)
    val timeTable: TimeTableEntity,
    @Column(name = "user_id", length = 128)
    val userId: String,
) : TimeBasedPrimaryKey() {
    @Enumerated(STRING)
    @Column(name = "occupied_status")
    var occupiedStatus: OccupyStatus = OCCUPIED
        protected set

    @Column(name = "occupied_datetime")
    val occupiedDatetime: LocalDateTime = LocalDateTime.now()

    @Column(name = "unoccupied_datetime")
    var unoccupiedDatetime: LocalDateTime? = null
        protected set

    fun unoccupied() {
        occupiedStatus = UNOCCUPIED
        unoccupiedDatetime = LocalDateTime.now()
    }
}
