package com.reservation.persistence.schedule.repository.adapter

import com.reservation.persistence.schedule.entity.HolidayEntity
import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.schedule.entity.TableEntity
import com.reservation.persistence.schedule.entity.TimeSpanEntity
import com.reservation.persistence.schedule.repository.jpa.HolidayJpaRepository
import com.reservation.persistence.schedule.repository.jpa.ScheduleJpaRepository
import com.reservation.persistence.schedule.repository.jpa.TableJpaRepository
import com.reservation.persistence.schedule.repository.jpa.TimeSpanJpaRepository
import com.reservation.persistence.schedule.repository.mutator.LoadScheduleResultMutator
import com.reservation.schedule.port.output.LoadSchedule
import com.reservation.schedule.port.output.LoadSchedule.LoadScheduleResult
import org.springframework.stereotype.Component

@Component
class LoadScheduleAdapter(
    private val scheduleJpaRepository: ScheduleJpaRepository,
    private val holidayJpaRepository: HolidayJpaRepository,
    private val tableJpaRepository: TableJpaRepository,
    private val timeSpanJpaRepository: TimeSpanJpaRepository,
) : LoadSchedule {

    private fun loadHolidays(restaurantId: String) =
        holidayJpaRepository.findAllByRestaurantId(restaurantId)

    private fun loadTables(restaurantId: String) =
        tableJpaRepository.findAllByRestaurantId(restaurantId)

    private fun loadTimeSpans(restaurantId: String) =
        timeSpanJpaRepository.findAllByRestaurantId(restaurantId)

    override fun query(restaurantId: String): LoadScheduleResult? {
        val schedule: ScheduleEntity = scheduleJpaRepository.findByRestaurantId(restaurantId)
            ?: return null

        val holidays: List<HolidayEntity> = loadHolidays(restaurantId)
        val tables: List<TableEntity> = loadTables(restaurantId)
        val timeSpans: List<TimeSpanEntity> = loadTimeSpans(restaurantId)


        return LoadScheduleResultMutator.mapLoadScheduleResult(
            schedule, holidays, tables, timeSpans
        )
    }
}
