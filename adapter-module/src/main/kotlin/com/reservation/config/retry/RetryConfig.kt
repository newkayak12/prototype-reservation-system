package com.reservation.config.retry

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor
import org.springframework.retry.annotation.EnableRetry
import org.springframework.retry.support.RetryTemplate

@Configuration
@EnableRetry
class RetryConfig {
    @Bean
    fun retryInterceptor(): AnnotationAwareRetryOperationsInterceptor =
        AnnotationAwareRetryOperationsInterceptor()

    @Bean
    fun retryTemplate(redisRetryContextCache: RedisRetryContextCache): RetryTemplate =
        RetryTemplate.defaultInstance().apply {
            setRetryContextCache(redisRetryContextCache)
        }

    @Bean
    fun listenRetryReason() = RetryReasonListener()
}
