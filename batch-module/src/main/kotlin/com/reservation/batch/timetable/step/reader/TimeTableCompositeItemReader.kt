package com.reservation.batch.timetable.step.reader

import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.batch.querydsl.QueryDslCursorItemReader
import com.reservation.batch.timetable.constants.TimeTableBatchConstants.JobParameter
import com.reservation.batch.timetable.dto.ScheduleWithData
import com.reservation.persistence.schedule.entity.HolidayEntity
import com.reservation.persistence.schedule.entity.QHolidayEntity.holidayEntity
import com.reservation.persistence.schedule.entity.QTableEntity.tableEntity
import com.reservation.persistence.schedule.entity.QTimeSpanEntity.timeSpanEntity
import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.schedule.entity.TableEntity
import com.reservation.persistence.schedule.entity.TimeSpanEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.annotation.BeforeStep
import org.springframework.batch.item.ExecutionContext
import org.springframework.batch.item.ItemStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TimeTableCompositeItemReader(
    private val scheduleReader: QueryDslCursorItemReader<ScheduleEntity>,
    private val entityManagerFactory: EntityManagerFactory,
) : ItemStreamReader<ScheduleWithData> {
    private lateinit var entityManager: EntityManager
    private lateinit var query: JPAQueryFactory
    private lateinit var targetMonth: LocalDate

    companion object {
        private val FORMAT = DateTimeFormatter.ISO_DATE
    }

    @BeforeStep
    fun beforeStep(stepExecution: StepExecution) {
        targetMonth = stepExecution.jobParameters.getString(JobParameter.DATE_KEY)
            ?.let { LocalDate.parse(it, FORMAT) }
            ?: LocalDate.now().withDayOfMonth(1)
    }

    override fun open(executionContext: ExecutionContext) {
        scheduleReader.open(executionContext)
        entityManager = entityManagerFactory.createEntityManager()
        query = JPAQueryFactory(entityManager)
    }

    override fun read(): ScheduleWithData? {
        val schedule = scheduleReader.read() ?: return null

        val holidays = findHolidays(schedule.restaurantId)
        val tables = findTables(schedule.restaurantId)
        val timeSpans = findTimeSpans(schedule.restaurantId)

        return ScheduleWithData(
            schedule,
            holidays,
            tables,
            timeSpans,
        )
    }

    private fun findHolidays(restaurantId: String): List<HolidayEntity> =
        query.selectFrom(holidayEntity)
            .where(
                holidayEntity.restaurantId.eq(restaurantId),
            )
            .fetch()

    private fun findTables(restaurantId: String): List<TableEntity> =
        query.selectFrom(tableEntity)
            .where(tableEntity.restaurantId.eq(restaurantId))
            .fetch()

    private fun findTimeSpans(restaurantId: String): List<TimeSpanEntity> =
        query.selectFrom(timeSpanEntity)
            .where(timeSpanEntity.restaurantId.eq(restaurantId))
            .fetch()

    override fun update(executionContext: ExecutionContext) {
        scheduleReader.update(executionContext)
    }

    override fun close() {
        scheduleReader.close()
        entityManager.close()
    }
}
