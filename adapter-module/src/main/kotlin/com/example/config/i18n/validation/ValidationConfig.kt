package com.example.config.i18n.validation

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean

@Configuration
internal class ValidationConfig {
    @Bean
    fun getValidator(messageSource: MessageSource): LocalValidatorFactoryBean =
        LocalValidatorFactoryBean()
            .apply {
                setValidationMessageSource(messageSource)
            }
}
