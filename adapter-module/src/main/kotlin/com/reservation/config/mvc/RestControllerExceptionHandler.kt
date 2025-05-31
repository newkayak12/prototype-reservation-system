package com.reservation.config.mvc

import com.reservation.exceptions.ClientException
import org.slf4j.LoggerFactory
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RestControllerExceptionHandler : EnvironmentAware {
    private val log = LoggerFactory.getLogger(this::class.java.name)
    private var isDevelopment: Boolean = false

    @ExceptionHandler(exception = [ClientException::class])
    fun clientException(exception: ClientException) {
        log.error("message: {}", exception.message)

        if (isDevelopment) {
            log.error("stacktrace: {}", exception.stackTrace.joinToString("\n"))
        }
    }

    override fun setEnvironment(environment: Environment) {
        val developmentProfile = arrayOf("temporary", "local", "test")
        isDevelopment = environment.matchesProfiles(*developmentProfile)
    }
}
