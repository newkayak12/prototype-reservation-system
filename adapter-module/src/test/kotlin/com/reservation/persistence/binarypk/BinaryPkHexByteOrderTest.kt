package com.reservation.persistence.binarypk

import com.reservation.persistence.restaurant.entity.RestaurantEntity
import com.reservation.utilities.generator.uuid.UuidGenerator
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.math.BigDecimal

/**
 * ADR-028 "확인(Confirmation)" 절이 요구하는 단일 회귀 테스트 — **SQL `HEX()`로 직접** 읽어서,
 * 바이트 순서가 틀리는 경우와 변환자가 아예 안 타는 경우를 **하나의 assertion**으로 함께 잡는다.
 *
 * [BinaryPkHibernateVerificationTest]는 클라이언트 쪽에서 `UuidBinaryCodec.encode`로 만든 바이트와
 * `ResultSet.getBytes`로 읽은 원시 바이트를 비교한다 — "코덱이 자기 자신과 같다"는 쪽에 가깝다.
 * 이 테스트는 그와 독립적인 경로로 같은 결론에 도달한다: **DB 엔진 자신의 `HEX()` 함수**가 계산한
 * 16진수 문자열을, 코덱을 거치지 않고 **Kotlin 문자열 자체에서 하이픈만 제거하고 대문자로 바꾼
 * 값**과 비교한다. 두 경로(앱 코드가 만든 기대값 vs DB가 읽은 실제 값) 중 어느 쪽에 버그가
 * 있어도 — 바이트 순서가 스왑됐어도, `UuidBinaryIdentifierType`/`UuidBinaryConverter`가 아예
 * 적용되지 않아 36자 평문이 그대로 들어갔어도 — 이 단일 assertion이 깨진다.
 *
 * ADR-028 "바이트 순서 근거": 스왑 없음(`UUID_TO_BIN(x,1)` 미사용) — 그래서 기대값은
 * `identifier.replace("-", "").uppercase()`이다. `UUID_TO_BIN` 스왑을 썼다면 이 비교가
 * 성립하지 않았을 것이다 — 즉 이 테스트는 "스왑을 안 썼다"는 결정 자체도 핀(pin)한다.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BinaryPkHexByteOrderTest {
    companion object {
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

    private lateinit var dataSource: HikariDataSource
    private lateinit var entityManagerFactory: EntityManagerFactory

    @BeforeAll
    fun setUp() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = mysqlContainer.jdbcUrl
                    username = mysqlContainer.username
                    password = mysqlContainer.password
                    maximumPoolSize = 4
                },
            )

        entityManagerFactory =
            LocalContainerEntityManagerFactoryBean()
                .apply {
                    this.dataSource = asDataSource()
                    setPackagesToScan(
                        "com.reservation.persistence.restaurant.entity",
                        "com.reservation.persistence.common",
                    )
                    jpaVendorAdapter = HibernateJpaVendorAdapter()
                    setJpaPropertyMap(
                        mapOf(
                            "hibernate.hbm2ddl.auto" to "create-drop",
                            "hibernate.dialect" to "org.hibernate.dialect.MySQL8Dialect",
                        ),
                    )
                }.also { it.afterPropertiesSet() }
                .`object`
    }

    private fun asDataSource(): DataSource = dataSource

    @AfterAll
    fun tearDown() {
        entityManagerFactory.close()
        dataSource.close()
    }

    @DisplayName(
        "저장된 id 컬럼을 SQL HEX()로 읽으면 Kotlin identifier 문자열에서 하이픈만 제거하고 " +
            "대문자로 바꾼 값과 정확히 같다 — 바이트 순서 오류와 변환자 미적용을 한 assertion으로 " +
            "함께 잡는다 (ADR-028 확인 절).",
    )
    @Test
    fun `HEX of stored id column equals hyphen-stripped upper-cased identifier string`() {
        val restaurant =
            RestaurantEntity(
                companyId = UuidGenerator.generate(),
                userId = UuidGenerator.generate(),
                name = "binary-pk-hex-order-verify",
                introduce = "",
                phone = "",
                zipCode = "",
                address = "",
                detail = "",
                latitude = BigDecimal.ZERO,
                longitude = BigDecimal.ZERO,
            )

        val em = entityManagerFactory.createEntityManager()
        em.transaction.begin()
        em.persist(restaurant)
        em.transaction.commit()
        em.close()

        val expectedHex = restaurant.identifier.replace("-", "").uppercase()
        assertEquals(
            32,
            expectedHex.length,
            "테스트 전제가 깨졌다 — 하이픈 제거 후 UUID 문자열은 항상 32자여야 한다",
        )

        dataSource.connection.use { connection ->
            connection
                .prepareStatement("SELECT HEX(id) AS hex_id FROM restaurant WHERE name = ?")
                .use { stmt ->
                    stmt.setString(1, "binary-pk-hex-order-verify")
                    stmt.executeQuery().use { rs ->
                        assertTrue(rs.next(), "persist된 restaurant row를 HEX() 조회로 못 읽었다")
                        val actualHex = rs.getString("hex_id")
                        assertEquals(
                            expectedHex,
                            actualHex,
                            "HEX(id)가 identifier 문자열의 하이픈-제거-대문자 변환과 다르다 — " +
                                "바이트 순서가 틀렸거나(ADR-028이 스왑을 쓰지 않기로 결정했는데 " +
                                "스왑이 섞여 들어갔거나) 변환자(UuidBinaryIdentifierType)가 전혀 " +
                                "적용되지 않아 평문 36자가 그대로 들어갔을 가능성이 있다",
                        )
                    }
                }
        }
    }
}
