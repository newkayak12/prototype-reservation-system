package com.reservation.event.timetable.occupancy

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import com.reservation.enumeration.OutboxEventType.TIME_TABLE_OCCUPIED
import com.reservation.enumeration.TableStatus
import com.reservation.enumeration.TimeTableConfirmStatus
import com.reservation.event.abstractEvent.AbstractEvent
import com.reservation.timetable.TimeTable
import com.reservation.timetable.port.input.command.request.CreateTimeTableOccupancyCommand
import com.reservation.timetable.port.output.CreateTimeTableOccupancy
import com.reservation.timetable.port.output.LoadBookableTimeTables
import com.reservation.timetable.usecase.CreateTimeTableOccupancyService
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.mockk.every
import io.mockk.verify
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import java.time.LocalTime

@SpringBootTest
@ActiveProfiles(value = ["test"])
@Testcontainers
class TimeTableOccupiedDomainEventListenerTest {
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

        @Container
        private val redisContainer =
            GenericContainer(DockerImageName.parse("redis:7.0"))
                .withExposedPorts(6379)

        @Container
        val kafkaContainer =
            KafkaContainer(
                DockerImageName.parse("apache/kafka:latest"),
            )

        @JvmStatic
        @DynamicPropertySource
        fun register(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysqlContainer.jdbcUrl }
            registry.add("spring.datasource.username") { mysqlContainer.username }
            registry.add("spring.datasource.password") { mysqlContainer.password }

            registry.add("spring.flyway.url") { mysqlContainer.jdbcUrl }
            registry.add("spring.flyway.user") { mysqlContainer.username }
            registry.add("spring.flyway.password") { mysqlContainer.password }

            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") {
                redisContainer.getMappedPort(6379)
            }
            registry.add("redisson.single-server-config.address") {
                "redis://${redisContainer.host}:${redisContainer.getMappedPort(6379)}"
            }

            registry.add("spring.kafka.bootstrap-servers") {
                kafkaContainer.bootstrapServers
            }
            registry.add("spring.kafka.producer.bootstrap-servers") {
                kafkaContainer.bootstrapServers
            }
            registry.add("spring.kafka.consumer.bootstrap-servers") {
                kafkaContainer.bootstrapServers
            }
        }

        private const val EVENT = "TIME_TABLE_OCCUPIED"
    }

    @MockkBean
    private lateinit var loadBookableTimeTables: LoadBookableTimeTables

    @MockkBean
    private lateinit var createTimeTableOccupancy: CreateTimeTableOccupancy

    @SpykBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, AbstractEvent>

    @Autowired
    private lateinit var createTimeTableOccupancyService: CreateTimeTableOccupancyService

    @Autowired
    private lateinit var kafkaAdmin: KafkaAdmin

    @PostConstruct
    fun createTopics() {
        try {
            // Kafka 컨테이너 시작 대기
            Thread.sleep(5000)
            val adminClient = AdminClient.create(kafkaAdmin.configurationProperties)
            val topic = NewTopic("TIME_TABLE_OCCUPIED", 3, 1)
            adminClient.createTopics(listOf(topic)).all().get()
            adminClient.close()
        } catch (e: Exception) {
            println("Topic creation failed: ${e.message}")
        }
    }

    private fun createValidTimeTable() =
        TimeTable(
            UuidGenerator.generate(),
            UuidGenerator.generate(),
            LocalDate.now(),
            LocalDate.now().dayOfWeek,
            LocalTime.of(11, 0),
            LocalTime.of(12, 0),
            1,
            1,
            TableStatus.EMPTY,
            TimeTableConfirmStatus.NOT_CONFIRMED,
            null,
        )

    @Test
    fun `when Publish Event`() {
        val command =
            CreateTimeTableOccupancyCommand(
                UuidGenerator.generate(),
                UuidGenerator.generate(),
                LocalDate.now(),
                LocalTime.of(11, 0),
            )

        every {
            loadBookableTimeTables.query(any())
        } returns listOf(createValidTimeTable())

        every {
            createTimeTableOccupancy.createTimeTableOccupancy(any())
        } returns UuidGenerator.generate()

        createTimeTableOccupancyService.execute(command)

        verify(exactly = 1) {
            kafkaTemplate.send(eq(TIME_TABLE_OCCUPIED.name), any(), any())
        }
    }
}
