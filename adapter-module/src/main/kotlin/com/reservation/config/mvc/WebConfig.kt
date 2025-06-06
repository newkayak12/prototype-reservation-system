package com.reservation.config.mvc

import com.reservation.config.i18n.HttpHeaderLocaleResolver
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import org.springframework.web.servlet.LocaleResolver

@Configuration
internal class WebConfig {
    @Bean
    fun localeResolver(): LocaleResolver = HttpHeaderLocaleResolver()

    @Bean
    fun LocalValidatorFactoryBean(messageSource: MessageSource): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean().apply {
            setValidationMessageSource(messageSource)
        }
}
