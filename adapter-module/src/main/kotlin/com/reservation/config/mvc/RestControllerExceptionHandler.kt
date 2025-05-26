package com.reservation.config.mvc

import com.reservation.exceptions.ClientException
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class RestControllerExceptionHandler {
    private val log = LoggerFactory.getLogger(this::class.java.name)

    @ExceptionHandler(exception = [ClientException::class])
    fun clientException(exception: ClientException) {
        log.error(exception.message)
    }
}
