package com.reservation.config.persistence.entity

import org.hibernate.annotations.IdGeneratorType

@IdGeneratorType(TimeBasedIdGenerator::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class TimeBasedUuidStrategy
