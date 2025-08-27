package com.reservation.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.reservation.config.mvc.RestControllerExceptionHandler
import jakarta.validation.Validation
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.StandardEnvironment
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.validation.beanvalidation.SpringValidatorAdapter

object MockMvcFactory {
    private val jacksonObjectMapper =
        ObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(WRITE_DATES_AS_TIMESTAMPS, false)
        }

    private val validatorFactory = Validation.buildDefaultValidatorFactory()
    private val validator = validatorFactory.validator
    private val converter = MappingJackson2HttpMessageConverter(jacksonObjectMapper)
    private val springValidator = SpringValidatorAdapter(validator)
    private val environment: ConfigurableEnvironment =
        StandardEnvironment().also { it.setActiveProfiles("test") }
    private val restControllerExceptionHandler =
        RestControllerExceptionHandler().also { it.setEnvironment(environment) }

    val objectMapper: ObjectMapper
        get() = jacksonObjectMapper.copy()

    private fun <T> getStandAloneSetup(controller: T) =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(converter)
            .setValidator(springValidator)
            .setControllerAdvice(restControllerExceptionHandler)

    fun <T> buildMockMvc(controller: T): MockMvc = getStandAloneSetup(controller).build()

    fun <T> buildMockMvc(
        controller: T,
        restDocumentation: ManualRestDocumentation,
    ): MockMvc =
        getStandAloneSetup(controller)
            .apply<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(
                MockMvcRestDocumentation.documentationConfiguration(restDocumentation),
            ).build()
}
