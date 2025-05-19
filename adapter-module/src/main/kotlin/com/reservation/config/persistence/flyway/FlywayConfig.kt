package com.reservation.config.persistence.flyway

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource
import org.springframework.boot.autoconfigure.flyway.FlywayProperties
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration

class FlywayConfig {
    companion object {
        val log = LoggerFactory.getLogger(this::class::java.name)
    }

    @Bean
    @FlywayDataSource
    @ConfigurationProperties("spring.flyway")
    fun flywayDataSource(flyway: FlywayProperties): DataSource = DataSourceBuilder.create()
        .url(flyway.url)
        .username(flyway.user)
        .password(flyway.password)
        .driverClassName(flyway.driverClassName)
        .build()
}
