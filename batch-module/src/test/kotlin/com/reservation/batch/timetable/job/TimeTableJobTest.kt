package com.reservation.batch.timetable.job

import com.reservation.batch.querydsl.QueryDslCursorItemReader
import com.reservation.batch.timetable.constants.TimeTableBatchConstants
import com.reservation.batch.timetable.dto.ScheduleWithData
import com.reservation.batch.timetable.job.TimeTableJobTest.JobConfig
import com.reservation.batch.timetable.job.TimeTableJobTest.StepConfig
import com.reservation.batch.timetable.step.processor.TimeTableItemProcessor
import com.reservation.batch.timetable.step.reader.TimeTableCompositeItemReader
import com.reservation.batch.timetable.step.writer.TimeTableJdbcItemWriter
import com.reservation.enumeration.ScheduleActiveStatus.ACTIVE
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import com.reservation.persistence.schedule.entity.QScheduleEntity.scheduleEntity
import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.schedule.entity.TableEntity
import com.reservation.persistence.schedule.entity.TimeSpanEntity
import com.reservation.persistence.timetable.entity.TimeTableEntity
import com.reservation.utilities.generator.uuid.UuidGenerator
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.repository.support.ResourcelessJobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.ContextConfiguration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Testcontainers
@ContextConfiguration(classes = [StepConfig::class, JobConfig::class])
@SpringBatchTest
@EnableBatchProcessing
class TimeTableJobTest {
    companion object {
        @Container
        private val mysqlContainer =
            MySQLContainer("mysql:8.0")
                .apply {
                    withDatabaseName("prototype_reservation")
                    withUsername("root")
                    withPassword("root")
                }

        private const val CHUNK_SIZE = 5

        private object Reader {
            const val SCHEDULE_NAME = "test_schedule-reader"
            const val COMPOSITE_NAME = "test_composite_reader"
        }

        private object Processor {
            const val NAME = "test_time_table_processor"
        }

        private object Writer {
            const val NAME = "test_time_table_writer"
        }
    }

    class StepConfig {
        @Bean
        fun dataSource(): DataSource {
            val dataSource =
                DataSourceBuilder.create()
                    .url(mysqlContainer.jdbcUrl)
                    .username(mysqlContainer.username)
                    .password(mysqlContainer.password)
                    .driverClassName("com.mysql.cj.jdbc.Driver")
                    .build()
            return dataSource
        }

        @Bean(name = ["entity-manager-factory"])
        fun entityManagerFactory(dataSource: DataSource): EntityManagerFactory {
            val factory = LocalContainerEntityManagerFactoryBean()
            factory.dataSource = dataSource
            factory.setPackagesToScan("com.reservation.persistence")
            factory.jpaVendorAdapter = HibernateJpaVendorAdapter()
            val properties =
                mutableMapOf<String, Any>(
                    "hibernate.hbm2ddl.auto" to "create-drop",
                    "hibernate.dialect" to "org.hibernate.dialect.MySQLDialect",
                    "hibernate.show_sql" to "true",
                    "hibernate.format_sql" to "true",
                )
            factory.jpaPropertyMap = properties
            factory.afterPropertiesSet()
            return factory.`object`!!
        }

        @Bean
        fun transactionManager(
            entityManagerFactory: EntityManagerFactory,
        ): PlatformTransactionManager {
            return JpaTransactionManager(entityManagerFactory)
        }

        @Bean(name = [Reader.SCHEDULE_NAME])
        @StepScope
        fun scheduleReader(entityManager: EntityManager): QueryDslCursorItemReader<ScheduleEntity> {
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
                            id?.let { scheduleEntity.restaurantId.gt(id) },
                        )
                        .orderBy(scheduleEntity.restaurantId.asc())
                },
                idExtractor = { it.restaurantId },
                chunkSize = 5,
            )
        }

        @Bean(Reader.COMPOSITE_NAME)
        @StepScope
        fun timeTableCompositeItemReader(
            @Qualifier(Reader.SCHEDULE_NAME)
            scheduleReader: QueryDslCursorItemReader<ScheduleEntity>,
            entityManager: EntityManager,
        ): TimeTableCompositeItemReader {
            return TimeTableCompositeItemReader(scheduleReader, entityManager)
        }

        @Bean(Processor.NAME)
        @StepScope
        fun timeTableItemProcessor(): TimeTableItemProcessor = TimeTableItemProcessor()

        @Bean(Writer.NAME)
        @StepScope
        fun timeTableJdbcItemWriter(dataSource: DataSource): TimeTableJdbcItemWriter =
            TimeTableJdbcItemWriter(dataSource)

        @Bean(TimeTableBatchConstants.Step.NAME)
        fun timeTableStep(
            @Qualifier(Reader.COMPOSITE_NAME)
            reader: TimeTableCompositeItemReader,
            @Qualifier(Processor.NAME)
            processor: TimeTableItemProcessor,
            @Qualifier(Writer.NAME)
            writer: TimeTableJdbcItemWriter,
            jobRepository: JobRepository,
            transactionManager: PlatformTransactionManager,
        ): Step {
            return StepBuilder(TimeTableBatchConstants.Step.NAME, jobRepository)
                .chunk<ScheduleWithData, List<TimeTableEntity>>(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build()
        }

        @Bean
        fun entityManager(entityManagerFactory: EntityManagerFactory): EntityManager =
            SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory)
    }

    class JobConfig {
        @Bean
        fun jobRepository(): JobRepository = ResourcelessJobRepository()

        @Bean
        fun jobLauncher(jobRepository: JobRepository): JobLauncher =
            TaskExecutorJobLauncher().apply {
                this.setJobRepository(jobRepository)
                this.afterPropertiesSet()
            }

        @Bean(TimeTableBatchConstants.Job.NAME)
        fun timeTableInsertJob(
            @Qualifier(value = TimeTableBatchConstants.Step.NAME) timeTableStep: Step,
            jobRepository: JobRepository,
        ): Job {
            return JobBuilder(TimeTableBatchConstants.Job.NAME, jobRepository)
                .start(timeTableStep)
                .build()
        }
    }

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var jobLauncherTestUtils: JobLauncherTestUtils

    @Autowired
    @Qualifier(TimeTableBatchConstants.Job.NAME)
    private lateinit var timeTableJob: Job

    @Test
    @Suppress("EmptyFunctionBlock")
    fun contextLoads() {
    }

    @DisplayName("배치 작업 테스트")
    @Nested
    open inner class BatchTest {
        private fun giveMeActiveScheduleFixture(restaurantId: String) =
            ScheduleEntity(
                restaurantId,
                tablesConfigured = true,
                workingHoursConfigured = true,
                holidaysConfigured = true,
                ACTIVE,
                2,
                8,
            )

        private fun giveMeInactiveScheduleFixture(restaurantId: String) =
            ScheduleEntity(
                restaurantId,
                tablesConfigured = true,
                workingHoursConfigured = true,
                holidaysConfigured = true,
                INACTIVE,
                2,
                8,
            )

        private fun giveMeTimeSpan(
            restaurantId: String,
            dayOfWeek: DayOfWeek,
        ) = listOf(
            TimeSpanEntity(restaurantId, dayOfWeek, LocalTime.of(13, 0), LocalTime.of(14, 0)),
            TimeSpanEntity(restaurantId, dayOfWeek, LocalTime.of(14, 0), LocalTime.of(15, 0)),
        )

        private fun giveMeTable(restaurantId: String) =
            listOf(
                TableEntity(restaurantId, 1, 4),
                TableEntity(restaurantId, 4, 4),
            )

        private fun prepareData() {
            val activeUUID = UuidGenerator.generate()
            val inactiveUUID = UuidGenerator.generate()
            val now = LocalDate.now()

            val activeSchedule = giveMeActiveScheduleFixture(activeUUID)
            val activeTimeSpans = giveMeTimeSpan(activeUUID, now.dayOfWeek)
            val activeTables = giveMeTable(activeUUID)
            val inactiveSchedule = giveMeInactiveScheduleFixture(inactiveUUID)
            val inactiveTimeSpans = giveMeTimeSpan(inactiveUUID, now.dayOfWeek)
            val inactiveTables = giveMeTable(inactiveUUID)

            entityManager.persist(activeSchedule)
            entityManager.persist(inactiveSchedule)
            activeTimeSpans.forEach { entityManager.persist(it) }
            activeTables.forEach { entityManager.persist(it) }
            inactiveTimeSpans.forEach { entityManager.persist(it) }
            inactiveTables.forEach { entityManager.persist(it) }
            entityManager.flush()
            entityManager.clear()

            checkDataInserted()
        }

        private fun checkDataInserted() {
            // 데이터 확인
            val schedules =
                entityManager.createQuery(
                    """
                    SELECT s FROM ScheduleEntity s where s.status='ACTIVE' order by s.restaurantId
                    """.trimIndent(),
                    ScheduleEntity::class.java,
                ).resultList
            println("Total schedules in DB: ${schedules.size}")
            schedules.forEach { schedule ->
                println(
                    """
                    Schedule: restaurantId=${schedule.restaurantId},
                    status=${schedule.status},
                    tablesConfigured=${schedule.tablesConfigured},
                    workingHoursConfigured=${schedule.workingHoursConfigured},
                    holidaysConfigured=${schedule.holidaysConfigured}
                    """.trimIndent(),
                )
            }
        }

        @BeforeEach
        fun jobLauncherTestUtilsSetUp() {
            jobLauncherTestUtils.job = timeTableJob
        }

        @Test
        @Suppress("EmptyFunctionBlock")
        @Transactional
        open fun launchJob() {
            prepareData()
            val jobParameters =
                JobParametersBuilder()
                    .addLocalDate(
                        TimeTableBatchConstants.JobParameter.DATE_KEY,
                        YearMonth.now().atDay(1),
                    )
                    .toJobParameters()

            val jobExecution: JobExecution = jobLauncherTestUtils.launchJob(jobParameters)

            assertEquals(jobExecution.status, BatchStatus.COMPLETED)
        }
    }
}
