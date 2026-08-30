package com.reservation.event.timetable.occupancy

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.SpykBean
import com.reservation.enumeration.OutboxEventType.TIME_TABLE_OCCUPIED
import com.reservation.timetable.port.input.ConfirmTimeTableOccupancyUseCase
import com.reservation.timetable.port.input.command.request.ConfirmTimeTableOccupancyCommand
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy
import com.reservation.timetable.port.output.ConfirmTimeTableOccupancy.ConfirmedOccupancy
import com.reservation.utilities.generator.uuid.UuidGenerator
import io.mockk.every
import io.mockk.verify
import jakarta.annotation.PostConstruct
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.ProducerRecord
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
    }

    @MockkBean
    private lateinit var confirmTimeTableOccupancy: ConfirmTimeTableOccupancy

    @SpykBean
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    /**
     * 진입점이 두 번 옮겨졌다.
     *
     * 원래는 사용자 요청 경로에서 저장과 발행이 같이 일어났다. Phase 3에서 저장이 컨슈머로
     * 넘어갔고, Phase 4에서는 발행이 다시 **확정 시점**으로 옮겨졌다 — 좌석을 잡는 시점의 점유는
     * 아직 PENDING이라, 거기서 발행하면 확정되지 않고 만료될 홀드까지 하류 예약으로 전파된다.
     *
     * 그래서 이 테스트가 보려는 outbox → Kafka 발행을 일으키려면 확정을 호출해야 한다.
     */
    @Autowired
    private lateinit var confirmTimeTableOccupancyUseCase: ConfirmTimeTableOccupancyUseCase

    @Autowired
    private lateinit var kafkaAdmin: KafkaAdmin

    @PostConstruct
    fun createTopics() {
        try {
            // Kafka 컨테이너 시작 대기
            Thread.sleep(5000)
            val adminClient = AdminClient.create(kafkaAdmin.configurationProperties)
            val topic = NewTopic(TIME_TABLE_OCCUPIED.name, 3, 1)
            adminClient.createTopics(listOf(topic)).all().get()
            adminClient.close()
        } catch (e: Exception) {
            println("Topic creation failed: ${e.message}")
        }
    }

    @Test
    fun `when Publish Event`() {
        val command =
            ConfirmTimeTableOccupancyCommand(
                UuidGenerator.generate(),
                UuidGenerator.generate(),
                LocalDate.now(),
                LocalTime.of(11, 0),
            )

        every {
            confirmTimeTableOccupancy.confirm(any())
        } returns
            ConfirmedOccupancy(
                timeTableId = UuidGenerator.generate(),
                timeTableOccupancyId = UuidGenerator.generate(),
            )

        confirmTimeTableOccupancyUseCase.execute(command)

        verify(exactly = 1) {
            kafkaTemplate.send(
                match<ProducerRecord<String, String>> {
                    it.topic() == TIME_TABLE_OCCUPIED.name
                },
            )
        }
    }
}
