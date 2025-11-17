package com.reservation.featureflag.configurations

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor
import org.springframework.retry.annotation.EnableRetry

@Configuration
@EnableAspectJAutoProxy
@EnableRetry
class TestRetryConfig {
    @Bean
    fun retryInterceptor(): AnnotationAwareRetryOperationsInterceptor =
        AnnotationAwareRetryOperationsInterceptor()

    @Bean
    fun listenRetryReason() = TestListenRetryReason()
}
