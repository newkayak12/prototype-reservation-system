package com.reservation.persistence.schedule.entity

import com.reservation.enumeration.ScheduleActiveStatus
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Table(
    catalog = "prototype_reservation",
    name = "schedule",
    indexes = [],
)
@Entity
@Suppress("LongParameterList")
class ScheduleEntity(
    @Id
    @Column(name = "id")
    val restaurantId: String,
    tablesConfigured: Boolean = false,
    workingHoursConfigured: Boolean = false,
    holidaysConfigured: Boolean = false,
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
    @field:Enumerated(EnumType.STRING)
    var status: ScheduleActiveStatus = status
        protected set

    @Column(name = "total_tables")
    var totalTables: Int = totalTables
        protected set

    @Column(name = "total_capacity")
    var totalCapacity: Int = totalCapacity
        protected set

    fun checkTablesConfigured(count: Int) {
        this.tablesConfigured = count > 0
        toggleActiveState()
    }

    fun checkWorkingHoursConfigured(count: Int) {
        this.workingHoursConfigured = count > 0
        toggleActiveState()
    }

    fun checkHolidaysConfigured(count: Int) {
        this.holidaysConfigured = count > 0
        toggleActiveState()
    }

    fun adjustTableInformation(
        totalTables: Int,
        totalCapacity: Int,
    ) {
        this.totalTables = totalTables
        this.totalCapacity = totalCapacity
    }

    private fun toggleActiveState() {
        this.status =
            this.status.rebalance(
                this.tablesConfigured,
                this.workingHoursConfigured,
                this.holidaysConfigured,
            )
    }
}
