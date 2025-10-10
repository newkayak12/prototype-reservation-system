package com.reservation

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ActiveProfiles(value = ["test"])
@Testcontainers
class ReservationBatchApplicationTest {
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
            GenericContainer(DockerImageName.parse("redis:7.0")).withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun register(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysqlContainer.jdbcUrl }
            registry.add("spring.datasource.username") { mysqlContainer.username }
            registry.add("spring.datasource.password") { mysqlContainer.password }
        }
    }

    @Test
    @Suppress("EmptyFunctionBlock")
    fun contextLoad() {
    }
}
