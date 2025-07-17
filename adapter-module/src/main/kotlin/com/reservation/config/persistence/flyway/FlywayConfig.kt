package com.reservation.config.persistence.flyway

import org.springframework.boot.autoconfigure.flyway.FlywayDataSource
import org.springframework.boot.autoconfigure.flyway.FlywayProperties
import org.springframework.boot.jdbc.DataSourceBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

@Profile("!generate")
@Configuration
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
