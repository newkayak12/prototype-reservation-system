package com.example.sample.config

import org.hibernate.annotations.IdGeneratorType

@IdGeneratorType(TimeBasedIdGenerator::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class TimeBasedUuidStrategy
