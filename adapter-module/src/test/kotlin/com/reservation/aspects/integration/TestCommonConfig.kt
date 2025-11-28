package com.reservation.aspects.integration

import com.reservation.config.aspect.SpelParser
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class TestCommonConfig {
    @Bean
    fun spelParser(): SpelParser = SpelParser()
}
