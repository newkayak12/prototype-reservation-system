package com.reservation.batch.timetable.step.processor

import com.reservation.batch.timetable.constants.TimeTableBatchConstants.JobParameter
import com.reservation.batch.timetable.dto.ScheduleWithData
import com.reservation.persistence.timetable.entity.TimeTableEntity
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.item.ItemProcessor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

open class TimeTableItemProcessor : ItemProcessor<ScheduleWithData, List<TimeTableEntity>> {
    private lateinit var targetYearMonth: YearMonth

    companion object {
        private const val YEAR_MONTH_PATTERN = "yyyy-MM"
        private val FORMAT = DateTimeFormatter.ofPattern(YEAR_MONTH_PATTERN)
    }

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        val month =
            stepExecution.jobParameters.getString(JobParameter.DATE_KEY)
                ?: LocalDate.now().format(FORMAT)

        targetYearMonth = YearMonth.parse(month)
    }

    private fun dateSequence(): List<LocalDate> {
        val startDate = targetYearMonth.atDay(1)
        val endDate = targetYearMonth.atEndOfMonth()

        return generateDateRange(startDate, endDate)
    }

    private fun generateDateRange(
        start: LocalDate,
        end: LocalDate,
    ): List<LocalDate> {
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(end) }
            .toList()
    }

    override fun process(item: ScheduleWithData): List<TimeTableEntity>? {
        val restaurantId = item.schedule.restaurantId
        val holiday = item.holidays.map { it.date }
        val dayOfTimeMap = item.timeSpans.groupBy { it.day }
        val dateSequence = dateSequence()
        val table = item.tables

        return dateSequence
            .filterNot { it in holiday }
            .flatMap { date ->
                val dayOfWeek = date.dayOfWeek
                val times =
                    dayOfTimeMap[dayOfWeek]
                        ?: return@flatMap emptyList<TimeTableEntity>()

                times.flatMap { time ->
                    table.map { table ->
                        TimeTableEntity(
                            restaurantId = restaurantId,
                            date = date,
                            day = dayOfWeek,
                            startTime = time.startTime,
                            endTime = time.endTime,
                            tableNumber = table.tableNumber,
                            tableSize = table.tableSize,
                        )
                    }
                }
            }
    }
}
