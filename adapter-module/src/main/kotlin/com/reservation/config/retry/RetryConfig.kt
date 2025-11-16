package com.reservation.config.retry

import org.springframework.aop.Advisor
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor
import org.springframework.retry.annotation.EnableRetry
import org.springframework.retry.annotation.Retryable

@Configuration
@EnableAspectJAutoProxy
@EnableRetry
class RetryConfig {
    @Bean
    fun retryInterceptor(): AnnotationAwareRetryOperationsInterceptor =
        AnnotationAwareRetryOperationsInterceptor()

    @Bean
    fun retryAdvisor(retryInterceptor: AnnotationAwareRetryOperationsInterceptor): Advisor {
        val pointcut =
            AnnotationMatchingPointcut(
                null,
                Retryable::class.java,
                true,
            )

        return DefaultPointcutAdvisor(pointcut, retryInterceptor)
    }
}
