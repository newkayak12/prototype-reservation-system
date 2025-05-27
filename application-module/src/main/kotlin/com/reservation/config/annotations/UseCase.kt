package com.reservation.config.annotations

import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import org.springframework.stereotype.Component

@Component
@Target(CLASS)
@Retention(RUNTIME)
annotation class UseCase
