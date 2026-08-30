package com.reservation.persistence.queue

import com.reservation.persistence.queue.repository.adapter.WaitingQueueDatabaseAdapter
import com.reservation.persistence.queue.repository.jpa.WaitingQueueJpaRepository
import com.reservation.queue.port.output.EnterWaitingQueue.Companion.ADMITTED_POSITION
import com.reservation.queue.port.output.WaitingQueueFallbackCoordinator
import com.reservation.queue.vo.WaitingQueueSlot
import com.reservation.utilities.generator.uuid.UuidGenerator
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ClassPathResource
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.datasource.init.DataSourceInitializer
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Redis 폴백 좌표계를 **진짜 MySQL** 위에서 검증한다.
 *
 * mock 기반 [WaitingQueueDatabaseAdapterTest]는 어댑터의 산술만 본다. 여기서는 JPQL이 실제로
 * 실행되는지, `WaitingQueueSlotProjection` 매핑이 동작하는지, 그리고 무엇보다
 * **여러 워커가 동시에 승격을 시도해도 정원을 넘지 않는지**를 본다.
 *
 * 격리 수준을 일부러 READ COMMITTED로 낮춰 둔 이유:
 * 폴백 승격의 정확성은 서버 격리 수준에 기대면 안 된다. MySQL 기본값인 REPEATABLE READ에서는
 * 트랜잭션 스냅샷이 우연히 "센다 → 고른다"를 일관되게 만들어 결함을 가려버리지만,
 * READ COMMITTED(Aurora 등에서 흔한 설정)에서는 두 워커가 각자 0을 세고 서로 다른 row를 골라
 * 정원의 두 배를 승격시킨다. `SELECT ... FOR UPDATE`가 그 창을 닫는다.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [
        WaitingQueueDatabaseAdapterIntegrationTest.WaitingQueuePersistenceConfiguration::class,
    ],
)
@Testcontainers
class WaitingQueueDatabaseAdapterIntegrationTest {
    companion object {
        private const val CAPACITY = 10
        private const val WORKER_SIZE = 8
        private const val WAITING_SIZE = 100
        private const val POOL_SIZE = 16
        private val TIME_TO_LIVE: Duration = Duration.ofMinutes(5)

        @JvmStatic
        @Container
        private val mysqlContainer =
            MySQLContainer("mysql:8.0")
                .apply {
                    withDatabaseName("prototype_reservation")
                    withUsername("root")
                    withPassword("root")
                }
    }

    @TestConfiguration
    @EnableJpaRepositories(
        basePackages = ["com.reservation.persistence.queue.repository.jpa"],
    )
    @EnableTransactionManagement
    class WaitingQueuePersistenceConfiguration {
        @Bean
        fun dataSource(): DataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = mysqlContainer.jdbcUrl
                    username = mysqlContainer.username
                    password = mysqlContainer.password
                    maximumPoolSize = POOL_SIZE
                    transactionIsolation = "TRANSACTION_READ_COMMITTED"
                },
            )

        /** 운영에서 쓰는 마이그레이션 DDL을 그대로 올린다. */
        @Bean
        fun dataSourceInitializer(dataSource: DataSource): DataSourceInitializer =
            DataSourceInitializer().apply {
                setDataSource(dataSource)
                setDatabasePopulator(
                    ResourceDatabasePopulator(
                        ClassPathResource("db/migration/V1_19__waiting_queue.sql"),
                        ClassPathResource("db/migration/V1_20__waiting_queue_user_id.sql"),
                    ),
                )
            }

        @Bean
        fun entityManagerFactory(dataSource: DataSource): LocalContainerEntityManagerFactoryBean =
            LocalContainerEntityManagerFactoryBean().apply {
                this.dataSource = dataSource
                setPackagesToScan("com.reservation.persistence.queue.entity")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
            }

        @Bean
        fun transactionManager(entityManagerFactory: EntityManagerFactory) =
            JpaTransactionManager(entityManagerFactory)

        @Bean
        fun waitingQueueFallbackCoordinator(
            waitingQueueJpaRepository: WaitingQueueJpaRepository,
        ): WaitingQueueFallbackCoordinator = WaitingQueueDatabaseAdapter(waitingQueueJpaRepository)
    }

    @Autowired
    private lateinit var coordinator: WaitingQueueFallbackCoordinator

    private fun newSlot() =
        WaitingQueueSlot(
            restaurantId = UuidGenerator.generate(),
            date = LocalDate.of(2026, 8, 26),
            startTime = LocalTime.of(11, 0),
        )

    /**
     * 진입의 멱등 기준이 사용자이므로 티켓마다 소유자가 하나씩 필요하다.
     * 같은 ticketId로 다시 부르면 같은 userId가 나오므로 "같은 사람의 재진입"이 된다.
     */
    private fun enter(
        slot: WaitingQueueSlot,
        ticketId: String,
    ) = coordinator.enter(slot, userIdOf(ticketId), ticketId)

    private fun userIdOf(ticketId: String) = "user-$ticketId"

    @DisplayName("진입한 순서대로 순번이 매겨지고 같은 티켓은 순번을 유지한다.")
    @Test
    fun `enter assigns position in order and stays idempotent`() {
        val slot = newSlot()
        val ticketIds = (1..5).map { UuidGenerator.generate() }

        val positions = ticketIds.map { enter(slot, it).position }

        assertEquals((1L..5L).toList(), positions)
        ticketIds.forEachIndexed { index, ticketId ->
            assertEquals(index + 1L, coordinator.findPosition(slot, ticketId))
            assertTrue(!coordinator.isAdmitted(slot, ticketId))
        }
        assertEquals(1L, enter(slot, ticketIds.first()).position)
    }

    @DisplayName("대기 중인 슬롯은 projection으로 조회된다.")
    @Test
    fun `load waiting slots through projection`() {
        val slot = newSlot()

        enter(slot, UuidGenerator.generate())

        assertTrue(coordinator.loadSlots().contains(slot))
    }

    @DisplayName("승격된 티켓은 ADMITTED가 되고 대기열에서 빠진다.")
    @Test
    fun `admitted ticket leaves the queue`() {
        val slot = newSlot()
        val ticketIds = (1..5).map { UuidGenerator.generate() }
        val capacity = 2

        ticketIds.forEach { enter(slot, it) }

        assertEquals(capacity, coordinator.admit(slot, capacity, TIME_TO_LIVE))

        ticketIds.take(capacity).forEach {
            assertTrue(coordinator.isAdmitted(slot, it))
            assertNull(coordinator.findPosition(slot, it))
        }
        ticketIds.drop(capacity).forEachIndexed { index, ticketId ->
            assertTrue(!coordinator.isAdmitted(slot, ticketId))
            assertEquals(index + 1L, coordinator.findPosition(slot, ticketId))
        }
    }

    @DisplayName("이미 입장이 허용된 티켓으로 다시 진입해도 대기열로 되돌아가지 않는다.")
    @Test
    fun `re-enter after admission does not requeue`() {
        val slot = newSlot()
        val ticketId = UuidGenerator.generate()

        enter(slot, ticketId)
        assertEquals(1, coordinator.admit(slot, 1, TIME_TO_LIVE))

        assertEquals(ADMITTED_POSITION, enter(slot, ticketId).position)
        assertNull(coordinator.findPosition(slot, ticketId))
        assertTrue(coordinator.isAdmitted(slot, ticketId))
    }

    /**
     * 여러 인스턴스의 스케줄러가 동시에 폴백 승격을 돌리는 상황.
     * `SELECT ... FOR UPDATE`가 없으면 각 워커가 서로 다른 후보를 골라 정원을 크게 넘긴다.
     */
    @DisplayName("여러 워커가 동시에 폴백 승격을 돌려도 정원을 넘기지 않는다.")
    @Test
    fun `concurrent fallback admit never exceeds capacity`() {
        val slot = newSlot()
        val ticketIds = (1..WAITING_SIZE).map { UuidGenerator.generate() }
        val admittedCounts = ConcurrentLinkedQueue<Int>()

        ticketIds.forEach { enter(slot, it) }

        val executor = Executors.newFixedThreadPool(WORKER_SIZE)
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(WORKER_SIZE)

        executor.use {
            repeat(WORKER_SIZE) {
                executor.submit {
                    try {
                        startLatch.await()
                        admittedCounts.add(coordinator.admit(slot, CAPACITY, TIME_TO_LIVE))
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            assertTrue(endLatch.await(60, SECONDS))
        }

        val actuallyAdmitted = ticketIds.count { coordinator.isAdmitted(slot, it) }

        assertEquals(CAPACITY, admittedCounts.sum(), "승격 보고 합계가 정원을 넘었다")
        assertEquals(CAPACITY, actuallyAdmitted, "실제 ADMITTED row 수가 정원을 넘었다")
        assertEquals(
            WAITING_SIZE - CAPACITY,
            ticketIds.count { coordinator.findPosition(slot, it) != null },
            "승격되지 않은 티켓은 유실 없이 대기열에 남아있어야 한다",
        )
    }
}
