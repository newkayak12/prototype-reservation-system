package com.reservation.batch.timetable.step

import com.reservation.batch.querydsl.QueryDslCursorItemReader
import com.reservation.batch.timetable.constants.TimeTableBatchConstants
import com.reservation.batch.timetable.dto.ScheduleWithData
import com.reservation.batch.timetable.step.processor.TimeTableItemProcessor
import com.reservation.batch.timetable.step.reader.TimeTableCompositeItemReader
import com.reservation.batch.timetable.step.writer.TimeTableJdbcItemWriter
import com.reservation.enumeration.ScheduleActiveStatus.ACTIVE
import com.reservation.persistence.schedule.entity.QScheduleEntity.scheduleEntity
import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.timetable.entity.TimeTableEntity
import jakarta.persistence.EntityManager
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@Configuration(value = "time_table_step_config")
class TimeTableStepConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
) {
    companion object {
        private const val CHUNK_SIZE = 5
    }

    @Bean(TimeTableBatchConstants.Reader.SCHEDULE_NAME)
    @StepScope
    fun scheduleEntityItemReader(
        entityManager: EntityManager,
    ): QueryDslCursorItemReader<ScheduleEntity> {
        return QueryDslCursorItemReader(
            entityManager = entityManager,
            queryFunction = { query, id ->
                query
                    .selectFrom(scheduleEntity)
                    .where(
                        scheduleEntity.tablesConfigured.isTrue,
                        scheduleEntity.workingHoursConfigured.isTrue,
                        scheduleEntity.holidaysConfigured.isTrue,
                        scheduleEntity.status.eq(ACTIVE),
                        id?.let { scheduleEntity.restaurantId.gt(it) },
                    )
                    .orderBy(scheduleEntity.restaurantId.asc())
            },
            idExtractor = { it.restaurantId },
            chunkSize = 5,
        )
    }

    @Bean(TimeTableBatchConstants.Reader.COMPOSITE_NAME)
    @StepScope
    fun compositeTimeTableItemReader(
        @Qualifier(TimeTableBatchConstants.Reader.SCHEDULE_NAME)
        scheduleReader: QueryDslCursorItemReader<ScheduleEntity>,
        entityManager: EntityManager,
    ): TimeTableCompositeItemReader = TimeTableCompositeItemReader(scheduleReader, entityManager)

    @Bean(TimeTableBatchConstants.Processor.NAME)
    @StepScope
    fun timeTableItemProcessor(): TimeTableItemProcessor = TimeTableItemProcessor()

    @Bean(TimeTableBatchConstants.Writer.NAME)
    @StepScope
    fun timeTableJdbcItemWriter(dataSource: DataSource): TimeTableJdbcItemWriter =
        TimeTableJdbcItemWriter(dataSource)

    @Bean(TimeTableBatchConstants.Step.NAME)
    fun timeTableStep(
        @Qualifier(TimeTableBatchConstants.Reader.COMPOSITE_NAME)
        reader: TimeTableCompositeItemReader,
        @Qualifier(TimeTableBatchConstants.Processor.NAME)
        processor: TimeTableItemProcessor,
        @Qualifier(TimeTableBatchConstants.Writer.NAME)
        writer: TimeTableJdbcItemWriter,
    ): Step {
        return StepBuilder(TimeTableBatchConstants.Step.NAME, jobRepository)
            .chunk<ScheduleWithData, List<TimeTableEntity>>(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build()
    }
}
