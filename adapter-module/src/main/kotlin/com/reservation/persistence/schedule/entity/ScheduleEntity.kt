package com.reservation.persistence.schedule.entity

import com.reservation.enumeration.ScheduleActiveStatus
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Table(
    catalog = "prototype_reservation",
    name = "schedule",
    indexes = [],
)
@Entity
class ScheduleEntity(
    @Id
    @Column(name = "id")
    val restaurantId: String,
    tablesConfigured: Boolean = false,
    workingHoursConfigured: Boolean = false,
    holidaysConfigured: Boolean =false,
    status: ScheduleActiveStatus = INACTIVE,
    totalTables: Int = 0,
    totalCapacity: Int = 0,
) {

    @Column(name = "tables_configured")
    var tablesConfigured: Boolean = tablesConfigured
        protected set

    @Column(name = "working_hours_configured")
    var workingHoursConfigured: Boolean = workingHoursConfigured
        protected set

    @Column(name = "holidays_configured")
    var holidaysConfigured: Boolean = holidaysConfigured
        protected set

    @Column(name = "status")
    var status: ScheduleActiveStatus = status
        protected set

    @Column(name = "total_tables")
    var totalTables: Int = totalTables
        protected set

    @Column(name = "total_capacity")
    var totalCapacity: Int = totalCapacity
        protected set

}
