package com.reservation.config

import com.reservation.config.i18n.HttpHeaderLocaleResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver

@Configuration
internal class WebConfig {
    @Bean
    fun localeResolver(): LocaleResolver = HttpHeaderLocaleResolver()
}
