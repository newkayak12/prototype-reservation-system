package com.reservation.config.persistence.flyway

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource
import org.springframework.boot.autoconfigure.flyway.FlywayProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
@ConditionalOnProperty(prefix = "spring.flyway", name = ["url"])
class FlywayConfig {
    @Bean
    @FlywayDataSource
    fun flywayDataSource(flyway: FlywayProperties): DataSource =
        DataSourceBuilder.create()
            .url(flyway.url)
            .username(flyway.user)
            .password(flyway.password)
            .driverClassName(flyway.driverClassName)
            .build()
}
