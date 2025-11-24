package com.reservation.config.annotations

import com.reservation.enumeration.LockType
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import java.util.concurrent.TimeUnit

@Target(FUNCTION)
@Retention(RUNTIME)
annotation class DistributedLock(
    val key: String,
    val lockType: LockType,
    val waitTime: Long,
    val waitTimeUnit: TimeUnit,
)
