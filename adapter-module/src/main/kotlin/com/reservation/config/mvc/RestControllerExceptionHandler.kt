package com.reservation.config.mvc

import com.reservation.exceptions.ClientException
import com.reservation.exceptions.InvalidSituationException
import com.reservation.exceptions.UnauthorizedException
import com.reservation.utilities.logger.loggerFactory
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RestControllerExceptionHandler : EnvironmentAware {
    private val log = loggerFactory<RestControllerExceptionHandler>()
    private var isDevelopment: Boolean = false

    private fun logger(exception: Exception) {
        log.error("message: {}", exception.message)

        if (isDevelopment) {
            log.error("stacktrace: {}", exception.stackTrace.joinToString("\n"))
        }
    }

    @ExceptionHandler(exception = [UnauthorizedException::class])
    fun unAuthorizedExceptionHandler(exception: UnauthorizedException): ResponseEntity<String> {
        return ResponseEntity("Unauthorized: ${exception.message}", HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(exception = [ClientException::class])
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun clientExceptionHandler(exception: ClientException) = logger(exception)

    @ExceptionHandler(exception = [InvalidSituationException::class])
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun invalidSituationExceptionHandler(exception: InvalidSituationException) = logger(exception)

    override fun setEnvironment(environment: Environment) {
        val developmentProfile = arrayOf("temporary", "local", "test")
        isDevelopment = environment.matchesProfiles(*developmentProfile)
    }
}
