package com.reservation.persistence.schedule.repository.adapter

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.persistence.schedule.entity.HolidayEntity
import com.reservation.persistence.schedule.entity.TableEntity
import com.reservation.persistence.schedule.entity.TimeSpanEntity
import com.reservation.persistence.schedule.repository.jpa.HolidayJpaRepository
import com.reservation.persistence.schedule.repository.jpa.ScheduleJpaRepository
import com.reservation.persistence.schedule.repository.jpa.TableJpaRepository
import com.reservation.persistence.schedule.repository.jpa.TimeSpanJpaRepository
import com.reservation.schedule.port.output.ChangeSchedule
import com.reservation.schedule.port.output.ChangeSchedule.HolidayInquiry
import com.reservation.schedule.port.output.ChangeSchedule.ScheduleInquiry
import com.reservation.schedule.port.output.ChangeSchedule.TableInquiry
import com.reservation.schedule.port.output.ChangeSchedule.TimeSpanInquiry
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Component

@Component
class ChangeScheduleAdapter(
    private val scheduleJpaRepository: ScheduleJpaRepository,
    private val holidayJpaRepository: HolidayJpaRepository,
    private val tableJpaRepository: TableJpaRepository,
    private val timeSpanJpaRepository: TimeSpanJpaRepository,
) : ChangeSchedule {
    private fun <T> delete(
        repository: CrudRepository<T, String>,
        entities: List<T>,
        predicate: (T) -> Boolean,
    ) {
        val delete = entities.filter(predicate)
        if (delete.isEmpty()) return

        repository.deleteAll(delete)
    }

    private fun <T, I> insert(
        repository: CrudRepository<T, String>,
        inquiries: List<I>,
        map: (I) -> T,
    ) {
        if (inquiries.isEmpty()) return

        val insert = inquiries.mapNotNull(map)
        repository.saveAll(insert)
    }

    override fun command(inquiry: ScheduleInquiry): Boolean {
        adjustHoliday(inquiry.restaurantId, inquiry.holidays)
        adjustTimeSpan(inquiry.restaurantId, inquiry.timeSpans)
        adjustTable(inquiry.restaurantId, inquiry.tables)

        return adjustSchedule(inquiry)
    }

    private fun adjustSchedule(inquiry: ScheduleInquiry): Boolean {
        val schedule =
            scheduleJpaRepository.findByRestaurantId(inquiry.restaurantId)
                ?: throw NoSuchPersistedElementException()

        var totalTables = 0
        var totalCapacity = 0

        for (element in inquiry.tables) {
            totalTables += element.tableNumber
            totalCapacity += element.tableSize
        }

        schedule.checkTablesConfigured(inquiry.tables.size)
        schedule.checkWorkingHoursConfigured(inquiry.timeSpans.size)
        schedule.checkHolidaysConfigured(inquiry.holidays.size)
        schedule.adjustTableInformation(totalTables, totalCapacity)

        return true
    }

    private fun adjustHoliday(
        restaurantId: String,
        inquiries: List<HolidayInquiry>,
    ) {
        val entities = holidayJpaRepository.findAllByRestaurantId(restaurantId)
        val latestIds = inquiries.filter { it.id != null }.mapNotNull { it.id }

        delete(holidayJpaRepository, entities) { latestIds.contains(it.id) }
        insert(holidayJpaRepository, inquiries) {
            HolidayEntity(
                restaurantId = it.restaurantId,
                date = it.date,
            )
        }
    }

    private fun adjustTimeSpan(
        restaurantId: String,
        inquiries: List<TimeSpanInquiry>,
    ) {
        val entities = timeSpanJpaRepository.findAllByRestaurantId(restaurantId)
        val latestIds = inquiries.filter { it.id != null }.mapNotNull { it.id }

        delete(timeSpanJpaRepository, entities) { latestIds.contains(it.id) }
        insert(timeSpanJpaRepository, inquiries) {
            TimeSpanEntity(
                restaurantId = it.restaurantId,
                day = it.day,
                startTime = it.startTime,
                endTime = it.endTime,
            )
        }
    }

    private fun adjustTable(
        restaurantId: String,
        inquiries: List<TableInquiry>,
    ) {
        val entities = tableJpaRepository.findAllByRestaurantId(restaurantId)
        val latestIds = inquiries.filter { it.id != null }.mapNotNull { it.id }

        delete(tableJpaRepository, entities) { latestIds.contains(it.id) }
        insert(tableJpaRepository, inquiries) {
            TableEntity(
                restaurantId = it.restaurantId,
                tableNumber = it.tableNumber,
                tableSize = it.tableSize,
            )
        }
    }
}
