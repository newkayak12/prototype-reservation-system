package com.reservation.persistence.timetable

import com.reservation.enumeration.TableStatus.EMPTY
import com.reservation.persistence.timetable.entity.TimeTableEntity
import com.reservation.persistence.timetable.entity.TimeTableOccupancyEntity
import com.reservation.persistence.timetable.repository.jpa.TimeTableJpaRepository
import com.reservation.persistence.timetable.repository.jpa.TimeTableOccupancyJpaRepository
import com.reservation.utilities.generator.uuid.UuidGenerator
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Properties
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/**
 * 실제 MySQL 위에서 좌석 점유의 **마지막 방어선**을 검증한다.
 *
 * 이 테스트가 Phase 4의 존재 이유다. `TimeTableSeatRedisAdapterTest`가 "Redis 카운터가 원자적인가"를
 * 증명한다면, 여기서 증명해야 하는 것은 **그 앞단이 전부 틀렸을 때도 같은 좌석이 두 번 팔리지 않는가**이다.
 *
 * Redis 카운터와 Kafka 키 순서 보장은 둘 다 조건부다. 순서 보장은 한 컨슈머 인스턴스 안에서만
 * 완전하고, 카운터는 Redis가 살아 있어야 믿을 수 있다. 그래서 DB에 두 겹을 더 뒀다.
 *
 * 1. `claimTimeTable`의 조건부 `UPDATE ... WHERE table_status = 'EMPTY'` — 빈 좌석을 가져가는
 *    순간을 직렬화한다. 갱신 건수가 곧 승패라서, 이긴 쪽만 1행을 받는다.
 * 2. `UNIQUE(timetable_id, active_marker)` — 그마저 건너뛴 경로가 있어도 DB가 거절한다.
 *
 * 두 겹은 역할이 다르다. 1번이 뚫리는지는 **진짜 동시 트랜잭션**을 때려 봐야 알고, 2번이 진짜로
 * 작동하는지는 1번을 일부러 우회해 봐야 안다. mock으로는 둘 다 증명할 수 없다.
 *
 * ## 스키마를 실제 마이그레이션으로 세우는 이유
 *
 * 검증 대상이 애플리케이션 코드가 아니라 **DDL 그 자체**다. 테스트에 DDL을 따로 적어 두면
 * 마이그레이션이 틀려도 테스트는 통과한다 — 그건 아무것도 증명하지 않는다. 그래서
 * `db/migration`의 실제 파일을 읽어서 적용한다.
 */
@ExtendWith(SpringExtension::class)
@ContextConfiguration(
    classes = [TimeTableOccupancyConcurrencyTest.OccupancyJpaTestConfiguration::class],
)
@Testcontainers
class TimeTableOccupancyConcurrencyTest {
    companion object {
        private const val DATABASE = "prototype_reservation"

        /** 좌석 수보다 요청이 훨씬 많아야 경합이 실제로 일어난다. */
        private const val SEAT_SIZE = 10
        private const val CONTENDER_SIZE = 80
        private const val POOL_SIZE = 16

        /**
         * 이 테스트가 필요로 하는 테이블만 만드는 최소 체인.
         *
         * - `V1_13` : `timetable`
         * - `V1_15` : `timetable_occupancy`
         * - `V1_16` : `outbox` (V1_17이 함께 건드리므로 필요)
         * - `V1_17` : `timetable.version` (엔티티의 `@Version`이 이 컬럼을 요구한다)
         * - `V1_21` : `released_at` / `active_marker` / `UNIQUE(timetable_id, active_marker)`
         */
        private val MIGRATIONS =
            listOf(
                "db/migration/V1_13__schedule_context.sql",
                "db/migration/V1_15__timetable.sql",
                "db/migration/V1_16__outbox.sql",
                "db/migration/V1_17__add_optimistic_lock_for_timetable.sql",
                "db/migration/V1_21__timetable_occupancy_hold.sql",
            )

        @JvmStatic
        @Container
        private val mysqlContainer =
            MySQLContainer(DockerImageName.parse("mysql:8.0.33"))
                .withDatabaseName(DATABASE)

        /**
         * `--` 줄 주석을 걷어내고 `;`로 문장을 나눈다.
         *
         * 대상 파일이 전부 순수 DDL이라 이 정도로 충분하다. 문자열 리터럴 안에 `;`가 들어가는
         * INSERT 계열 마이그레이션은 [MIGRATIONS]에 포함하지 않는다.
         */
        private fun statementsOf(resource: String): List<String> {
            val sql =
                checkNotNull(
                    TimeTableOccupancyConcurrencyTest::class.java
                        .classLoader
                        .getResourceAsStream(resource),
                ) { "마이그레이션을 클래스패스에서 찾지 못했다: $resource" }
                    .bufferedReader()
                    .readText()

            return sql.lineSequence()
                .filterNot { it.trimStart().startsWith("--") }
                .joinToString("\n")
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        private fun migrate(dataSource: DataSource) {
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    MIGRATIONS.flatMap { statementsOf(it) }
                        .forEach { statement.execute(it) }
                }
            }
        }
    }

    @TestConfiguration
    @EnableJpaRepositories(basePackageClasses = [TimeTableJpaRepository::class])
    class OccupancyJpaTestConfiguration {
        @Bean
        fun dataSource(): DataSource {
            val dataSource =
                DataSourceBuilder.create()
                    .url(mysqlContainer.jdbcUrl)
                    .username(mysqlContainer.username)
                    .password(mysqlContainer.password)
                    .driverClassName(mysqlContainer.driverClassName)
                    .build()

            migrate(dataSource)

            return dataSource
        }

        @Bean
        fun entityManagerFactory(dataSource: DataSource): LocalContainerEntityManagerFactoryBean =
            LocalContainerEntityManagerFactoryBean().apply {
                this.dataSource = dataSource
                setPackagesToScan(TimeTableEntity::class.java.packageName)
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                // 스키마는 마이그레이션이 이미 만들었다. Hibernate가 손대면 검증 대상이 바뀐다.
                setJpaProperties(
                    Properties().apply { setProperty("hibernate.hbm2ddl.auto", "none") },
                )
            }

        @Bean
        fun transactionManager(
            entityManagerFactory: EntityManagerFactory,
        ): PlatformTransactionManager = JpaTransactionManager(entityManagerFactory)
    }

    @Autowired
    private lateinit var timeTableJpaRepository: TimeTableJpaRepository

    @Autowired
    private lateinit var timeTableOccupancyJpaRepository: TimeTableOccupancyJpaRepository

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private lateinit var transactionTemplate: TransactionTemplate

    private val date = LocalDate.of(2026, 8, 26)
    private val startTime = LocalTime.of(11, 0)
    private val endTime = LocalTime.of(13, 0)

    @BeforeEach
    fun init() {
        transactionTemplate = TransactionTemplate(transactionManager)
    }

    /** 테스트마다 새 매장을 써서 서로의 좌석을 보지 않게 한다. */
    private fun seedSeats(
        restaurantId: String,
        count: Int,
    ): List<TimeTableEntity> =
        (1..count).map { tableNumber ->
            timeTableJpaRepository.save(
                TimeTableEntity(
                    restaurantId = restaurantId,
                    date = date,
                    day = DayOfWeek.WEDNESDAY,
                    startTime = startTime,
                    endTime = endTime,
                    tableNumber = tableNumber,
                    tableSize = 4,
                    tableStatus = EMPTY,
                ),
            )
        }

    /**
     * 프로덕션 경로를 그대로 흉내낸다 — 빈 좌석 한 행을 조건부 갱신으로 가져가고, 그 행에 점유를
     * 만든다.
     *
     * 가져가기와 저장이 한 트랜잭션 안에 있어야 의미가 있다. 가져간 행의 잠금을 놓은 뒤에
     * 저장하면 그 사이가 그대로 경합 창이 된다.
     */
    private fun tryOccupy(
        restaurantId: String,
        userId: String,
    ): Boolean =
        transactionTemplate.execute {
            val claimed =
                timeTableJpaRepository.findBookableTimeTable(
                    restaurantId = restaurantId,
                    date = date,
                    startTime = startTime,
                )
                    .shuffled()
                    .firstOrNull { timeTableJpaRepository.claimTimeTable(it.identifier) == 1 }
                    ?: return@execute false

            // 벌크 UPDATE가 영속성 컨텍스트를 비웠으므로 방금 가져간 행은 준영속이다.
            // 프로덕션 경로(`CreateTimeTableOccupancyAdapter`)와 똑같이 다시 읽어서 붙인다.
            val timeTable =
                timeTableJpaRepository.findTimeTableEntityByIdentifierEquals(claimed.identifier)
                    ?: return@execute false

            timeTableOccupancyJpaRepository.save(
                TimeTableOccupancyEntity(timeTable = timeTable, userId = userId),
            )
            true
        } ?: false

    private fun liveOccupanciesOf(restaurantId: String): List<TimeTableOccupancyEntity> =
        timeTableOccupancyJpaRepository.findAll()
            .filter { it.timeTable.restaurantId == restaurantId }
            .filter { it.releasedAt == null }

    private fun runConcurrently(
        times: Int,
        block: (Int) -> Unit,
    ) {
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(times)
        val executor = Executors.newFixedThreadPool(POOL_SIZE)

        executor.use {
            repeat(times) { index ->
                executor.submit {
                    try {
                        startLatch.await()
                        block(index)
                    } finally {
                        endLatch.countDown()
                    }
                }
            }

            startLatch.countDown()
            endLatch.await(60, SECONDS)
        }
    }

    @DisplayName("좌석보다 훨씬 많은 사용자가 동시에 달려들어도 좌석 수만큼만 점유된다.")
    @Test
    fun `concurrent occupancy never exceeds seat size`() {
        val restaurantId = UuidGenerator.generate()
        seedSeats(restaurantId, SEAT_SIZE)

        val succeeded = AtomicInteger()
        runConcurrently(CONTENDER_SIZE) {
            // 잠금 대기 중 데드락으로 죽는 트랜잭션은 "실패"로 세면 된다 —
            // 여기서 증명할 것은 "성공이 좌석 수를 넘지 않는가"이다.
            val occupied = runCatching { tryOccupy(restaurantId, UuidGenerator.generate()) }
            if (occupied.getOrDefault(false)) succeeded.incrementAndGet()
        }

        val live = liveOccupanciesOf(restaurantId)
        assertEquals(SEAT_SIZE, succeeded.get())
        assertEquals(SEAT_SIZE, live.size)
        // 한 좌석에 살아 있는 점유가 둘 이상 붙으면 그게 곧 이중예약이다.
        assertEquals(SEAT_SIZE, live.map { it.timeTable.identifier }.toSet().size)
    }

    @DisplayName("조건부 갱신을 우회해도 같은 좌석에 살아 있는 점유를 두 건 만들 수 없다.")
    @Test
    fun `unique constraint rejects a second live occupancy`() {
        val restaurantId = UuidGenerator.generate()
        val timeTable = seedSeats(restaurantId, 1).first()

        timeTableOccupancyJpaRepository.save(
            TimeTableOccupancyEntity(timeTable = timeTable, userId = UuidGenerator.generate()),
        )

        // 앞단(Redis 카운터, Kafka 키 순서, 조건부 갱신)이 전부 실패해 같은 행에 두 번째 점유가
        // 들어오는 상황. 이때 DB가 거절하지 않으면 마지막 방어선이 없는 것이다.
        val second =
            runCatching {
                timeTableOccupancyJpaRepository.save(
                    TimeTableOccupancyEntity(
                        timeTable = timeTable,
                        userId = UuidGenerator.generate(),
                    ),
                )
            }

        assertTrue(second.exceptionOrNull() is DataIntegrityViolationException)
        assertEquals(1, liveOccupanciesOf(restaurantId).size)
    }

    @DisplayName("점유를 풀면 같은 좌석에 새 점유가 들어오고, 풀린 이력은 남는다.")
    @Test
    fun `released occupancy frees the seat and keeps history`() {
        val restaurantId = UuidGenerator.generate()
        val timeTable = seedSeats(restaurantId, 1).first()

        val first =
            timeTableOccupancyJpaRepository.save(
                TimeTableOccupancyEntity(timeTable = timeTable, userId = UuidGenerator.generate()),
            )
        first.release()
        timeTableOccupancyJpaRepository.save(first)

        // released_at이 채워지면 active_marker가 NULL이 되어 유니크 제약에서 빠진다.
        timeTableOccupancyJpaRepository.save(
            TimeTableOccupancyEntity(timeTable = timeTable, userId = UuidGenerator.generate()),
        )

        val all =
            timeTableOccupancyJpaRepository.findAll()
                .filter { it.timeTable.restaurantId == restaurantId }

        // 살아 있는 점유는 하나, 그러나 이력은 지워지지 않고 두 건이 남는다.
        assertEquals(1, all.count { it.releasedAt == null })
        assertEquals(2, all.size)
    }

    @DisplayName("같은 좌석을 두고 동시에 달려들어도 정확히 한 명만 가져간다.")
    @Test
    fun `single seat is sold exactly once under contention`() {
        val restaurantId = UuidGenerator.generate()
        seedSeats(restaurantId, 1)

        val results = ConcurrentLinkedQueue<Boolean>()
        runConcurrently(CONTENDER_SIZE) {
            results.add(
                runCatching { tryOccupy(restaurantId, UuidGenerator.generate()) }
                    .getOrDefault(false),
            )
        }

        assertEquals(1, results.count { it })
        assertEquals(1, liveOccupanciesOf(restaurantId).size)
    }
}
