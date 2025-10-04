package com.reservation.enumeration

enum class ScheduleActiveStatus {
    ACTIVE,
    INACTIVE,
    ;

    fun toggle(
        tablesConfigured: Boolean,
        workingHoursConfigured: Boolean,
        holidaysConfigured: Boolean,
    ): ScheduleActiveStatus {
        return when (this) {
            ACTIVE -> INACTIVE
            INACTIVE -> activate(tablesConfigured, workingHoursConfigured, holidaysConfigured)
        }
    }

    private fun activate(
        tablesConfigured: Boolean,
        workingHoursConfigured: Boolean,
        holidaysConfigured: Boolean,
    ): ScheduleActiveStatus {
        if (tablesConfigured && workingHoursConfigured && holidaysConfigured) return ACTIVE

        return INACTIVE
    }
}
