package com.reservation.batch.annotations

import java.lang.annotation.ElementType.TYPE
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy.RUNTIME
import java.lang.annotation.Target

@Target(TYPE)
@Retention(RUNTIME)
annotation class Step
