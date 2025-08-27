package com.reservation.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.reservation.config.mvc.RestControllerExceptionHandler
import jakarta.validation.Validation
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.StandardEnvironment
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.validation.beanvalidation.SpringValidatorAdapter

/**
 * 병렬 테스트 안전 MockMvc 컴포넌트
 * - 모든 컴포넌트가 thread-safe하고 immutable
 * - 싱글톤으로 메모리 효율성 확보
 * - ValidatorFactory 리소스 관리 포함
 */
object MockMvcComponent {

    // Thread-safe ObjectMapper (immutable after creation)
    private val jacksonObjectMapper: ObjectMapper =
        ObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(WRITE_DATES_AS_TIMESTAMPS, false)
        }

    // ValidatorFactory 리소스 관리
    private val validatorFactory = Validation.buildDefaultValidatorFactory().also {
        // JVM 종료 시 자동 정리
        Runtime.getRuntime().addShutdownHook(Thread {
            runCatching { it.close() }
        })
    }

    private val validator = validatorFactory.validator

    // Thread-safe 컴포넌트들
    internal val converter = MappingJackson2HttpMessageConverter(jacksonObjectMapper)
    internal val springValidator = SpringValidatorAdapter(validator)

    private val environment: ConfigurableEnvironment =
        StandardEnvironment().also {
            it.setActiveProfiles("test")
        }

    internal val restControllerExceptionHandler =
        RestControllerExceptionHandler().also {
            it.setEnvironment(environment)
        }

    /**
     * ObjectMapper 복사본 제공 (필요시에만)
     * 대부분의 경우 원본을 직접 사용해도 안전
     */
    fun copyObjectMapper(): ObjectMapper = jacksonObjectMapper.copy()
}
