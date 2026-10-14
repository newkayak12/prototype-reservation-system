package com.reservation.persistence.binarypk

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import javax.sql.DataSource
import java.sql.Connection
import java.sql.DriverManager

/**
 * `batch-module/src/test/resources/application-test.yaml:29`의
 * `ddl-auto: create-drop`이 조용히 벌릴 수 있는 drift를 직접 잡는다.
 *
 * `create-drop`은 Hibernate가 **엔티티 매핑만 보고** 스키마를 만든다 — Flyway는
 * 전혀 끼지 않는다. 그래서 batch 스위트가 초록이어도 "엔티티 매핑이 실제로 운영
 * Flyway 스키마와 같은 모양을 내는가"는 batch 스위트 자체로는 증명되지 않는다 —
 * 둘이 각자 다른 길로 같은 결론(또는 다른 결론)에 도달할 뿐이다. 이 테스트는
 * **같은 컨테이너**에서 두 경로를 순서대로 돌려 직접 비교한다:
 *
 * 1. **Phase A** — Hibernate `ddl-auto=create-drop`이 `com.reservation.persistence`
 *    전체를 스캔해 스키마를 만든다. `batch-module`의 `@SpringBootApplication`
 *    (`ReservationBatchApplication`, base package `com.reservation`)이 기동 시
 *    스캔하는 것과 **같은 범위**다 — batch-module 자신은 `@Entity`를 하나도
 *    선언하지 않고 `infrastructure-module`의 엔티티를 그대로 쓴다.
 * 2. **Phase B** — 스키마를 드롭한 뒤, 같은 컨테이너에 **실제 Flyway**로 전체
 *    체인(`classpath:db/migration`, V1_0~V1_23)을 적용한다 — U6가 검증한
 *    운영 경로 그대로다.
 * 3. 두 phase에서 `information_schema.COLUMNS`를 직접 읽어,
 *    [[binary-pk-identifier-inventory]]가 CONVERT로 판정한 대표 식별자 컬럼들이
 *    **양쪽 다 `binary(16)`** 인지, 그리고 LEAVE-AS-VARCHAR로 판정된 컬럼들이
 *    **양쪽 다 binary가 아닌지**(과변환 방지 음성 대조)를 비교한다.
 *
 * 전수(41개 표면)가 아니라 18개 PK 테이블 전부 + 대표 FK/암묵 컬럼 다수 + 음성
 * 대조 4개를 고른 표본이다 — 전수 비교는 [[binary-pk-association-verdicts]]가
 * 이미 코드 리딩으로 끝냈고, 이 테스트의 역할은 "코드가 말하는 것"과 "DB가
 * 실제로 만드는 것"이 일치하는지를 **실행으로** 확인하는 것이다. 불일치는
 * 숨기지 않고 전부 모아 한 번에 보고한다 — 하나만 틀려도 나머지를 가리면
 * "녹색 batch 스위트가 스키마 일치의 증거"라는 바로 그 함정에 다시 빠진다.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BatchCreateDropFlywaySchemaDivergenceTest {
    companion object {
        private const val SCHEMA = "prototype_reservation"

        @JvmStatic
        @Container
        private val mysqlContainer =
            MySQLContainer("mysql:8.0")
                .apply {
                    withDatabaseName(SCHEMA)
                    withUsername("root")
                    withPassword("root")
                }

        /** CONVERT 판정 표본 — PK 18개 전부 + 대표 FK/암묵 컬럼. */
        private val CONVERT_SAMPLE =
            listOf(
                // PK (inventory §1, 18개 전부)
                "restaurant" to "id",
                "restaurant_cuisines" to "id",
                "restaurant_tags" to "id",
                "restaurant_nationalities" to "id",
                "restaurant_photo" to "id",
                "holiday" to "id",
                "table" to "id",
                "time_span" to "id",
                "withdrawal_user" to "id",
                "reservation" to "id",
                "user_access_history" to "id",
                "user_change_history" to "id",
                "user" to "id",
                "menu" to "id",
                "menu_photo" to "id",
                "timetable_occupancy" to "id",
                "timetable" to "id",
                "company" to "id",
                // 명시 FK (inventory §2a/§2c)
                "restaurant_working_day" to "restaurant_id",
                "user_access_history" to "user_uuid",
                "user_change_history" to "user_uuid",
                "restaurant" to "company_id",
                "restaurant" to "user_id",
                "timetable" to "restaurant_id",
                "menu" to "restaurant_id",
                "holiday" to "restaurant_id",
                "table" to "restaurant_id",
                "time_span" to "restaurant_id",
                "timetable_occupancy" to "user_id",
                "waiting_queue" to "restaurant_id",
                "waiting_queue" to "user_id",
                "reservation" to "user_id",
                "reservation" to "restaurant_id",
                "reservation" to "timetable_id",
                "reservation" to "timetable_occupancy_id",
                // 순수 암묵 @JoinColumn (inventory §3a)
                "restaurant_cuisines" to "restaurant_id",
                "restaurant_photo" to "restaurant_id",
                "restaurant_nationalities" to "restaurant_id",
                "restaurant_tags" to "restaurant_id",
                "menu_photo" to "menu_id",
                "timetable_occupancy" to "timetable_id",
            )

        /** LEAVE-AS-VARCHAR 음성 대조 — 과변환되지 않았음을 같은 방식으로 확인한다. */
        private val LEAVE_AS_VARCHAR_SAMPLE =
            listOf(
                "user" to "login_id",
                "withdrawal_user" to "login_id",
                "waiting_queue" to "ticket_id",
                "user_change_history" to "user_id",
            )

        private fun rawJdbcConnection(): Connection =
            DriverManager.getConnection(
                mysqlContainer.jdbcUrl,
                mysqlContainer.username,
                mysqlContainer.password,
            )

        private fun columnType(
            connection: Connection,
            table: String,
            column: String,
        ): String? =
            connection
                .prepareStatement(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                ).use { stmt ->
                    stmt.setString(1, SCHEMA)
                    stmt.setString(2, table)
                    stmt.setString(3, column)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("COLUMN_TYPE") else null
                    }
                }

        private fun snapshot(
            samples: List<Pair<String, String>>,
        ): Map<Pair<String, String>, String?> =
            rawJdbcConnection().use { connection ->
                samples.associateWith { (table, column) ->
                    columnType(connection, table, column)
                }
            }

        private fun dropSchema() {
            rawJdbcConnection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("DROP DATABASE `$SCHEMA`")
                    statement.execute("CREATE DATABASE `$SCHEMA`")
                }
            }
        }

        private fun migrateWithFlyway(dataSource: DataSource) {
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }

        private fun checkConvertMismatches(
            hibernateConvert: Map<Pair<String, String>, String?>,
            flywayConvert: Map<Pair<String, String>, String?>,
            mismatches: MutableList<String>,
        ) {
            CONVERT_SAMPLE.forEach { site ->
                val (table, column) = site
                val hibernateType = hibernateConvert[site]
                val flywayType = flywayConvert[site]
                if (hibernateType == null || flywayType == null) {
                    mismatches +=
                        "$table.$column — 한쪽 스키마에 컬럼이 없다 " +
                        "(hibernate=$hibernateType, flyway=$flywayType)"
                } else if (!hibernateType.equals("binary(16)", ignoreCase = true)) {
                    mismatches +=
                        "$table.$column — hibernate create-drop이 " +
                        "BINARY(16)이 아니다: $hibernateType"
                } else if (!flywayType.equals("binary(16)", ignoreCase = true)) {
                    mismatches +=
                        "$table.$column — flyway 마이그레이션 결과가 " +
                        "BINARY(16)이 아니다: $flywayType"
                } else if (hibernateType != flywayType) {
                    mismatches +=
                        "$table.$column — hibernate=$hibernateType, " +
                        "flyway=$flywayType (다름)"
                }
            }
        }

        private fun checkLeaveAsVarcharMismatches(
            hibernateLeave: Map<Pair<String, String>, String?>,
            flywayLeave: Map<Pair<String, String>, String?>,
            mismatches: MutableList<String>,
        ) {
            LEAVE_AS_VARCHAR_SAMPLE.forEach { site ->
                val (table, column) = site
                val hibernateType = hibernateLeave[site]
                val flywayType = flywayLeave[site]
                if (hibernateType == null || flywayType == null) {
                    mismatches +=
                        "$table.$column (LEAVE-AS-VARCHAR) — 한쪽 스키마에 " +
                        "컬럼이 없다 (hibernate=$hibernateType, flyway=$flywayType)"
                } else if (hibernateType.contains("binary", ignoreCase = true)) {
                    mismatches +=
                        "$table.$column (LEAVE-AS-VARCHAR) — hibernate가 " +
                        "과변환했다: $hibernateType"
                } else if (flywayType.contains("binary", ignoreCase = true)) {
                    mismatches +=
                        "$table.$column (LEAVE-AS-VARCHAR) — flyway 쪽이 " +
                        "과변환됐다: $flywayType"
                }
            }
        }
    }

    private lateinit var hikari: com.zaxxer.hikari.HikariDataSource

    @BeforeAll
    fun setUp() {
        hikari =
            com.zaxxer.hikari.HikariDataSource(
                com.zaxxer.hikari.HikariConfig().apply {
                    jdbcUrl = mysqlContainer.jdbcUrl
                    username = mysqlContainer.username
                    password = mysqlContainer.password
                    maximumPoolSize = 4
                },
            )
    }

    @AfterAll
    fun tearDown() {
        hikari.close()
    }

    @DisplayName(
        "batch-module과 같은 범위(com.reservation.persistence 전체)로 Hibernate " +
            "create-drop이 만든 식별자 컬럼 타입이, 같은 컨테이너에 실제 Flyway로 " +
            "적용한 전체 마이그레이션 체인(V1_0~V1_23)이 만든 타입과 정확히 " +
            "같다 — 한쪽만 binary(16)이거나, 한쪽이 varchar로 남아 있으면 이 " +
            "테스트가 그 사실을 이름까지 지목해서 보고한다.",
    )
    @Test
    fun `hibernate create-drop schema matches flyway-migrated schema for identifier columns`() {
        // Phase A: Hibernate create-drop, 전체 persistence 패키지 스캔
        // (batch-module과 같은 범위).
        val hibernateEmf =
            LocalContainerEntityManagerFactoryBean()
                .apply {
                    dataSource = hikari
                    setPackagesToScan("com.reservation.persistence")
                    jpaVendorAdapter = HibernateJpaVendorAdapter()
                    setJpaPropertyMap(
                        mapOf(
                            "hibernate.hbm2ddl.auto" to "create-drop",
                            "hibernate.dialect" to "org.hibernate.dialect.MySQL8Dialect",
                        ),
                    )
                }.also { it.afterPropertiesSet() }
                .`object`

        val hibernateConvert = snapshot(CONVERT_SAMPLE)
        val hibernateLeave = snapshot(LEAVE_AS_VARCHAR_SAMPLE)
        hibernateEmf.close() // create-drop: close()가 DROP까지 수행한다.

        // 혹시 drop이 안 됐거나 일부만 지워진 상태로 Flyway가 겹쳐 올라가는
        // 사고를 막는다.
        dropSchema()

        // Phase B: 실제 Flyway로 전체 체인 적용.
        migrateWithFlyway(hikari)

        val flywayConvert = snapshot(CONVERT_SAMPLE)
        val flywayLeave = snapshot(LEAVE_AS_VARCHAR_SAMPLE)

        val mismatches = mutableListOf<String>()
        checkConvertMismatches(hibernateConvert, flywayConvert, mismatches)
        checkLeaveAsVarcharMismatches(hibernateLeave, flywayLeave, mismatches)

        assertTrue(
            mismatches.isEmpty(),
            "batch-module의 ddl-auto:create-drop 스키마와 Flyway 마이그레이션 " +
                "스키마가 ${mismatches.size}개 식별자 컬럼에서 어긋난다:\n" +
                mismatches.joinToString("\n") { "  - $it" },
        )
    }

    @DisplayName(
        "Flyway로 전체 체인을 적용한 스키마 위에서 ddl-auto:validate로 " +
            "EntityManagerFactory가 예외 없이 부팅된다 — 엔티티 매핑과 " +
            "마이그레이션 스키마가 전체적으로 일치함을 end-to-end로 확인한다.",
    )
    @Test
    fun `entityManagerFactory boots with ddl-auto validate against the fully-migrated schema`() {
        dropSchema()
        migrateWithFlyway(hikari)

        val validateEmf =
            LocalContainerEntityManagerFactoryBean()
                .apply {
                    dataSource = hikari
                    setPackagesToScan("com.reservation.persistence")
                    jpaVendorAdapter = HibernateJpaVendorAdapter()
                    setJpaPropertyMap(
                        mapOf(
                            "hibernate.hbm2ddl.auto" to "validate",
                            "hibernate.dialect" to "org.hibernate.dialect.MySQL8Dialect",
                        ),
                    )
                }

        // afterPropertiesSet()이 validate 실패 시 예외를 던진다 — 예외가 없으면
        // 그 자체가 "엔티티 매핑 == 마이그레이션 스키마"의 증거다.
        validateEmf.afterPropertiesSet()
        validateEmf.`object`.close()
    }
}
