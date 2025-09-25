package com.reservation.config.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

@Configuration
internal class JacksonConfig {
    @Bean
    @Primary
    fun objectMapper(): ObjectMapper =
        ObjectMapper().apply {
            // Kotlin 지원
            registerModule(KotlinModule.Builder().build())

            // Java Time 지원
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

            // Kotlin 기본값 처리
            configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false,
            )
        }
}
