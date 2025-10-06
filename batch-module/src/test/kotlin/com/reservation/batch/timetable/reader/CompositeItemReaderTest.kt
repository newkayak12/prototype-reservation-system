package com.reservation.batch.timetable.reader

import com.reservation.batch.querydsl.QueryDslCursorItemReader
import com.reservation.batch.timetable.dto.ScheduleWithData
import com.reservation.batch.timetable.step.reader.TimeTableCompositeItemReader
import com.reservation.enumeration.ScheduleActiveStatus.ACTIVE
import com.reservation.persistence.schedule.entity.QScheduleEntity.scheduleEntity
import com.reservation.persistence.schedule.entity.ScheduleEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Test
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemStreamReader
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@ActiveProfiles(value = ["test"])
@Testcontainers
class CompositeItemReaderTest {
    companion object {
        @Container
        private val mysqlContainer =
            MySQLContainer("mysql:8.0")
                .apply {
                    withDatabaseName("prototype_reservation")
                    withUsername("root")
                    withPassword("root")
                    withInitScript("docker-entrypoint-initdb.d/init.sql")
                }

//        @JvmStatic
//        @DynamicPropertySource
//        fun register(registry: DynamicPropertyRegistry) {
// //            registry.add("spring.datasource.url") { mysqlContainer.jdbcUrl }
//            registry.add("spring.datasource.username") { mysqlContainer.username }
//            registry.add("spring.datasource.password") { mysqlContainer.password }
//        }
    }

    @TestConfiguration
    inner class TestTimeTableStepConfig {
        @Bean("schedule_entity_item_reader")
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

        @Bean("composite_time_table_item_reader")
        @StepScope
        fun compositeTimeTableItemReader(
            @Qualifier("schedule_entity_item_reader")
            scheduleReader: QueryDslCursorItemReader<ScheduleEntity>,
            entityManagerFactory: EntityManagerFactory,
        ): ItemStreamReader<ScheduleWithData> =
            TimeTableCompositeItemReader(scheduleReader, entityManagerFactory)
    }

    @Autowired
    lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    lateinit var testEntityManager: EntityManager

    @Autowired
    lateinit var compositeTimeTableItemReader: ItemStreamReader<ScheduleWithData>

    @Test
    @Suppress("EmptyFunctionBlock")
    fun contextLoad() {}
}
