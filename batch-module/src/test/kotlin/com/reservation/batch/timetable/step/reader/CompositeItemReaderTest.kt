package com.reservation.batch.timetable.step.reader

import com.reservation.batch.querydsl.QueryDslCursorItemReader
import com.reservation.batch.timetable.step.reader.CompositeItemReaderTest.TestReaderConfig
import com.reservation.enumeration.ScheduleActiveStatus.ACTIVE
import com.reservation.enumeration.ScheduleActiveStatus.INACTIVE
import com.reservation.persistence.schedule.entity.QScheduleEntity.scheduleEntity
import com.reservation.persistence.schedule.entity.ScheduleEntity
import com.reservation.persistence.schedule.entity.TableEntity
import com.reservation.persistence.schedule.entity.TimeSpanEntity
import com.reservation.utilities.generator.uuid.UuidGenerator
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepExecution
import org.springframework.batch.item.ExecutionContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Testcontainers
@ContextConfiguration(classes = [TestReaderConfig::class])
@ExtendWith(SpringExtension::class)
class CompositeItemReaderTest {
    companion object {
        @Container
        private val mysqlContainer =
            MySQLContainer("mysql:8.0")
                .apply {
                    withDatabaseName("prototype_reservation")
                    withUsername("root")
                    withPassword("root")
                }
    }

    class TestReaderConfig {
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

        @Bean(name = ["schedule-reader"])
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

        @Bean
        fun timeTableCompositeItemReader(
            @Qualifier("schedule-reader")
            scheduleReader: QueryDslCursorItemReader<ScheduleEntity>,
            entityManager: EntityManager,
        ): TimeTableCompositeItemReader {
            return TimeTableCompositeItemReader(scheduleReader, entityManager)
        }

        @Bean
        fun exposedEntityManager(entityManagerFactory: EntityManagerFactory): EntityManager =
            SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory)
    }

    @Autowired
    private lateinit var timeTableCompositeItemReader: TimeTableCompositeItemReader

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    @Suppress("EmptyFunctionBlock")
    fun contextLoad() {
        // 단순 컨텍스트 로드 테스트
    }

    @DisplayName("Reader를 호출하며")
    @Nested
    open inner class `Call Reader` {
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

        fun prepareData() {
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

        private fun createStepExecutionManually(): StepExecution {
            val jobParams =
                JobParametersBuilder()
                    .addString("DATE_KEY", LocalDate.now().toString())
                    .toJobParameters()

            val jobExecution = JobExecution(1L, jobParams)
            return StepExecution("step", jobExecution)
        }

        @Test
        @Transactional
        open fun `should read ScheduleWithData correctly`() {
            // given
            prepareData()

            // StepExecution 설정
            val stepExecution = createStepExecutionManually()
            timeTableCompositeItemReader.beforeStep(stepExecution)

            // when
            timeTableCompositeItemReader.open(ExecutionContext())
            val result = timeTableCompositeItemReader.read()
            timeTableCompositeItemReader.close()

            // then
            assertThat(result).isNotNull
            assertThat(result?.schedule).isNotNull
            assertThat(result?.holidays).isEmpty()
            assertThat(result?.tables).isNotEmpty
            assertThat(result?.timeSpans).isNotEmpty
        }
    }
}
