package com.reservation.aspects.featureflag.configurations

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor
import org.springframework.retry.annotation.EnableRetry
import org.springframework.retry.policy.MapRetryContextCache

@Configuration
@EnableRetry
class TestRetryConfig {
    @Bean
    fun mapRetryContextCache() = MapRetryContextCache()

    @Bean(name = ["retryAdvisor"])
    fun retryAdvisor(
        mapRetryContextCache: MapRetryContextCache,
    ): AnnotationAwareRetryOperationsInterceptor {
        val interceptor = AnnotationAwareRetryOperationsInterceptor()
        interceptor.setRetryContextCache(mapRetryContextCache)

        return interceptor
    }

    @Bean
    fun listenRetryReason() = TestListenRetryReason()
}
