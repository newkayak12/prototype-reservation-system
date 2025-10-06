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
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemStreamReader
import org.springframework.batch.item.ItemWriter
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

    @Bean
    @StepScope
    fun scheduleEntityItemReader(
        entityManagerFactory: EntityManagerFactory,
    ): QueryDslCursorItemReader<ScheduleEntity> {
        return QueryDslCursorItemReader(
            entityManagerFactory = entityManagerFactory,
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

    @Bean
    @StepScope
    fun compositeTimeTableItemReader(
        scheduleReader: QueryDslCursorItemReader<ScheduleEntity>,
        entityManagerFactory: EntityManagerFactory,
    ): ItemStreamReader<ScheduleWithData> =
        TimeTableCompositeItemReader(scheduleReader, entityManagerFactory)

    @Bean
    @StepScope
    fun timeTableItemProcessor(): ItemProcessor<ScheduleWithData, List<TimeTableEntity>> =
        TimeTableItemProcessor()

    @Bean
    @StepScope
    fun timeTableJdbcItemWriter(dataSource: DataSource): ItemWriter<List<TimeTableEntity>> =
        TimeTableJdbcItemWriter(dataSource)

    @Bean(TimeTableBatchConstants.Step.NAME)
    fun timeTableStep(
        reader: ItemStreamReader<ScheduleWithData>,
        processor: ItemProcessor<ScheduleWithData, List<TimeTableEntity>>,
        writer: ItemWriter<List<TimeTableEntity>>,
    ): Step {
        return StepBuilder(TimeTableBatchConstants.Step.NAME, jobRepository)
            .chunk<ScheduleWithData, List<TimeTableEntity>>(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build()
    }
}
