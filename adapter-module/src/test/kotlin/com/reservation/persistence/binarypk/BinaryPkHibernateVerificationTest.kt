package com.reservation.persistence.binarypk

import com.reservation.persistence.restaurant.entity.RestaurantCuisinesEntity
import com.reservation.persistence.restaurant.entity.RestaurantEntity
import com.reservation.persistence.restaurant.entity.RestaurantWorkingDayEntity
import com.reservation.persistence.restaurant.entity.RestaurantWorkingDayId
import com.reservation.utilities.generator.uuid.UuidBinaryCodec
import com.reservation.utilities.generator.uuid.UuidGenerator
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
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
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * ADR-028 "확인(Confirmation)" 절, 잔존 위험 1·2를 직접 검증한다 —
 * Hibernate가 `@MappedSuperclass`의 `@Id`(`TimeBasedPrimaryKey.identifier`)와,
 * `@MapsId`로 그 `@Id` 매핑을 공유하는 `@EmbeddedId`(`RestaurantWorkingDayId`)
 * FK 컬럼 양쪽에서 실제로 변환자를 적용하는지를, 코드를 읽는 것이 아니라
 * Hibernate가 스스로 만든 스키마에 실제로 바이트를 쓰게 해서 확인한다.
 *
 * Flyway는 쓰지 않는다 — `hibernate.hbm2ddl.auto=create-drop`으로 Hibernate가
 * 현재 Kotlin 매핑만 가지고 스스로 테이블을 만들게 한다. 그래서 이 테스트는
 * V1_22/V1_23 마이그레이션의 존재 여부와 무관하게 돈다 — batch-module 테스트
 * 프로필이 이미 같은 방식(`create-drop`)을 쓴다(inventory §6).
 *
 * 검증 방법: JPA로 persist한 뒤, 같은 `DataSource`로 **raw JDBC**를 열어
 * 저장된 원시 바이트를 직접 읽는다. `EntityManager.find()`로 다시 읽으면
 * 변환자를 한 번 더 거쳐 항상 String으로 돌아오므로 "DB에 실제로 뭐가
 * 들어갔는지"를 증명하지 못한다 — 이 테스트가 raw JDBC를 쓰는 이유다.
 *
 * 실패 시 처리(ADR-028 결정 3): 바이트가 다르거나 insert 자체가 타입 오류로
 * 실패하면 지정 폴백으로 전환해야 한다. 이 테스트가 green인 한 전환은
 * 필요하지 않다 — 실제로는 `@Id`/`@EmbeddedId` 케이스에서 JPA `@Convert`가
 * 무시됨을 이 테스트로 먼저 확인했고(아래 2번째 실패 기록 참고), 그래서
 * [com.reservation.persistence.converter.UuidBinaryIdentifierType]
 * (Hibernate `UserType`)로 이미 전환한 상태에서 이 테스트가 통과한다.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BinaryPkHibernateVerificationTest {
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
                    // 이 두 패키지만 스캔한다 — @Id 케이스(RestaurantEntity, via
                    // TimeBasedPrimaryKey), @MapsId로 그 PK를 공유하는 @EmbeddedId
                    // 케이스(RestaurantWorkingDayEntity/Id), 순수 암묵 @JoinColumn
                    // 케이스(RestaurantCuisinesEntity) 모두 이 범위 안에 있다.
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

    private fun newRestaurant(name: String) =
        RestaurantEntity(
            companyId = UuidGenerator.generate(),
            userId = UuidGenerator.generate(),
            name = name,
            introduce = "",
            phone = "",
            zipCode = "",
            address = "",
            detail = "",
            latitude = BigDecimal.ZERO,
            longitude = BigDecimal.ZERO,
        )

    @DisplayName(
        "TimeBasedPrimaryKey(@MappedSuperclass @Id)가 실제로 변환돼 BINARY(16) " +
            "원시 바이트로 저장된다 (ADR-028 잔존 위험 1).",
    )
    @Test
    fun `id column stores converter bytes, not plaintext`() {
        val restaurant = newRestaurant("binary-pk-verify-id")

        val em = entityManagerFactory.createEntityManager()
        em.transaction.begin()
        em.persist(restaurant)
        em.transaction.commit()
        em.close()

        val expected = UuidBinaryCodec.encode(restaurant.identifier)
        assertEquals16Bytes(expected)

        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT id FROM restaurant WHERE name = ?").use { stmt ->
                stmt.setString(1, "binary-pk-verify-id")
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next(), "persist된 restaurant row를 raw JDBC로 못 읽었다")
                    assertArrayEquals(
                        expected,
                        rs.getBytes("id"),
                        "@MappedSuperclass @Id의 변환이 적용되지 않았다 — " +
                            "ADR-028 결정 3에 따라 폴백으로 전환해야 한다",
                    )
                }
            }
        }
    }

    @DisplayName(
        "@EmbeddedId(RestaurantWorkingDayId)가 @MapsId로 공유하는 @ManyToOne FK " +
            "컬럼도 같은 변환자를 통해 BINARY(16)으로 저장된다 (ADR-028 잔존 위험 2, " +
            "inventory 3a#1).",
    )
    @Test
    fun `mapsId-shared fk column stores converter bytes too`() {
        val restaurant = newRestaurant("binary-pk-verify-workingday")
        val workingDayId =
            RestaurantWorkingDayId(identifier = restaurant.identifier, day = DayOfWeek.MONDAY)

        val em = entityManagerFactory.createEntityManager()
        em.transaction.begin()
        em.persist(restaurant)
        em.persist(
            RestaurantWorkingDayEntity(
                id = workingDayId,
                restaurant = restaurant,
                startTime = LocalTime.of(9, 0),
                endTime = LocalTime.of(18, 0),
            ),
        )
        em.transaction.commit()
        em.close()

        val expected = UuidBinaryCodec.encode(restaurant.identifier)

        dataSource.connection.use { connection ->
            connection
                .prepareStatement("SELECT restaurant_id FROM restaurant_working_day WHERE day = ?")
                .use { stmt ->
                    stmt.setString(1, "MONDAY")
                    stmt.executeQuery().use { rs ->
                        assertTrue(rs.next(), "persist된 working_day row를 raw JDBC로 못 읽었다")
                        assertArrayEquals(
                            expected,
                            rs.getBytes("restaurant_id"),
                            "@MapsId가 공유하는 FK 컬럼에서 변환이 적용되지 않았다 (평문 텍스트가 " +
                                "그대로 들어갔거나 타입/길이 오류가 났을 가능성) — ADR-028 결정 " +
                                "3에 따라 폴백으로 전환해야 한다",
                        )
                    }
                }
        }
    }

    @DisplayName(
        "순수 암묵 @JoinColumn(RestaurantCuisinesEntity.restaurant_id, " +
            "columnDefinition 재선언 없음)도 참조되는 @Id의 타입과 길이를 그대로 " +
            "물려받아 BINARY(16)으로 저장된다 (inventory 3a#2, 7개 중 대표 사례).",
    )
    @Test
    fun `pure implicit joinColumn inherits full binary16 definition, length included`() {
        val restaurant = newRestaurant("binary-pk-verify-cuisines")

        val em = entityManagerFactory.createEntityManager()
        em.transaction.begin()
        em.persist(restaurant)
        em.persist(RestaurantCuisinesEntity(restaurant = restaurant, cuisinesId = 1L))
        em.transaction.commit()
        em.close()

        val expected = UuidBinaryCodec.encode(restaurant.identifier)

        dataSource.connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT restaurant_id FROM restaurant_cuisines WHERE cuisines_id = ?",
                )
                .use { stmt ->
                    stmt.setLong(1, 1L)
                    stmt.executeQuery().use { rs ->
                        assertTrue(rs.next(), "persist된 restaurant_cuisines row를 raw JDBC로 못 읽었다")
                        assertArrayEquals(
                            expected,
                            rs.getBytes("restaurant_id"),
                            "순수 암묵 @JoinColumn이 참조 PK의 BINARY(16) 정의(타입+길이)를 " +
                                "그대로 물려받지 못했다 — inventory 3a절의 전제가 깨진다",
                        )
                    }
                }
        }
    }

    private fun assertEquals16Bytes(bytes: ByteArray) {
        assertTrue(bytes.size == 16, "UuidBinaryCodec.encode 출력이 16바이트가 아니다 — 테스트 전제 깨짐")
    }
}
