package com.reservation.config.security

import org.slf4j.LoggerFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles(value = ["test"])
@TestConfiguration
@EnableWebSecurity
class TestSecurity {
    private val log = LoggerFactory.getLogger(this::class.java.simpleName)

    @Bean
    fun filterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        log.warn("SECURITY TEST ENABLED")
        return httpSecurity
            .cors { it.disable() }
            .csrf { it.disable() }
            .exceptionHandling { it.disable() }
            .headers { it.frameOptions { i -> i.sameOrigin() } }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .build()
    }
}
