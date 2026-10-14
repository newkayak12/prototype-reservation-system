package com.reservation.persistence.binarypk

import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.restaurant.entity.QRestaurantEntity.restaurantEntity
import com.reservation.persistence.restaurant.entity.RestaurantEntity
import com.reservation.persistence.restaurant.repository.dsl.RestaurantQuerySpec
import com.reservation.utilities.generator.uuid.UuidGenerator
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.persistence.EntityManagerFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
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
 * ADR-028 잔존 위험 3(키셋 페이지네이션의 정렬/범위비교 의미론 변화)과
 * `docs/v2/analysis/binary-pk-query-audit.md`의 "ORDERING SEMANTICS" 결론을
 * 직접 핀(pin)한다 — `restaurantEntity.identifier`가 BINARY(16)으로 바뀐 뒤에도
 * `.gt(cursor)` + `orderBy(...)` 키셋 커서 구성이 "모든 행을 정확히 한 번씩,
 * 스킵·중복 없이" 돌려주는지를 실제 MySQL(바이트 비교)로 확인한다.
 *
 * 정렬 방향은 오름차순(`asc()`)을 쓴다 — `RestaurantQuerySpec.identifierAfter`
 * (`.gt()`)는 production에서 `FindRestaurantsRepository.kt:157`의
 * `orderBy(...desc())`와 짝지어 쓰이지만, 그 조합은 "직전 페이지의 가장 작은
 * 값보다 큰 값"을 다시 내림차순으로 요청하는 모양이라 연속 페이지네이션에는
 * 쓸 수 없다(직전 페이지를 그대로 되돌려준다) — 이 비일관성은 BINARY(16) 전환과
 * 무관한 기존 상태이고 이 작업의 범위 밖이라 고치지 않는다(감사 문서에 별도
 * 기록). 대신 `batch-module`의 `TimeTableStepConfig`가 쓰는, 앞으로 전진하는
 * 표준 키셋 패턴(`.gt(cursor)` + `orderBy(...asc())`)과 동일한 모양으로
 * `identifierAfter`(production 함수, 수정 없이 재사용)를 검증한다.
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BinaryPkKeysetPaginationTest {
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

        private const val TOTAL_ROWS = 7
        private const val PAGE_SIZE = 3L
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
        "BINARY(16)으로 전환된 restaurant.id에 대해 `.gt(cursor)` + " +
            "`orderBy(identifier.asc())` 키셋 커서를 반복 적용하면, 모든 행을 " +
            "정확히 한 번씩, 스킵·중복 없이, 오름차순으로 돌려준다.",
    )
    @Test
    fun `keyset pagination over converted identifier visits every row exactly once in order`() {
        val inserted = insertRestaurants()
        val expectedOrder = groundTruthOrder()

        assertEquals(
            TOTAL_ROWS,
            expectedOrder.size,
            "ground truth 조회가 insert한 행 수와 다르다 — 테스트 전제가 깨졌다",
        )
        assertEquals(
            inserted.map { it.identifier }.toSet(),
            expectedOrder.toSet(),
            "ground truth가 insert한 identifier 집합과 다르다",
        )

        val visited = paginateAllIdentifiers()

        assertEquals(
            expectedOrder,
            visited,
            "키셋 페이지네이션 결과가 ground truth 순서와 다르다 — 스킵·중복 또는 " +
                "순서 불일치가 있다 (BINARY(16) 바이트 비교가 기대한 오름차순과 " +
                "어긋났을 가능성)",
        )
        assertEquals(
            visited.size,
            visited.toSet().size,
            "페이지를 넘기는 동안 같은 identifier를 두 번 이상 방문했다 (중복)",
        )
    }

    private fun insertRestaurants(): List<RestaurantEntity> {
        val em = entityManagerFactory.createEntityManager()
        em.transaction.begin()
        val inserted =
            (1..TOTAL_ROWS).map { index ->
                newRestaurant("binary-pk-keyset-$index").also { em.persist(it) }
            }
        em.transaction.commit()
        em.close()
        return inserted
    }

    /** Ground truth: 전체를 바이트 비교 오름차순으로 한 번에 읽은 결과. */
    private fun groundTruthOrder(): List<String> {
        val groundTruthEm = entityManagerFactory.createEntityManager()
        val expectedOrder =
            JPAQueryFactory(groundTruthEm)
                .select(restaurantEntity.identifier)
                .from(restaurantEntity)
                .where(RestaurantQuerySpec.isNotDeleted())
                .orderBy(restaurantEntity.identifier.asc())
                .fetch()
        groundTruthEm.close()
        return expectedOrder
    }

    /**
     * production의 RestaurantQuerySpec.identifierAfter(.gt)를 그대로 재사용하고,
     * orderBy만 전진 방향(asc)으로 맞춘 키셋 페이지네이션.
     */
    private fun paginateAllIdentifiers(): List<String> {
        val visited = mutableListOf<String>()
        var cursor = ""
        var guard = 0
        while (guard++ < TOTAL_ROWS + 1) {
            val pageEm = entityManagerFactory.createEntityManager()
            val page =
                JPAQueryFactory(pageEm)
                    .select(restaurantEntity.identifier)
                    .from(restaurantEntity)
                    .where(
                        RestaurantQuerySpec.isNotDeleted(),
                        RestaurantQuerySpec.identifierAfter(cursor),
                    )
                    .orderBy(restaurantEntity.identifier.asc())
                    .limit(PAGE_SIZE)
                    .fetch()
            pageEm.close()

            if (page.isEmpty()) break
            visited += page
            cursor = page.last()
        }
        return visited
    }
}
