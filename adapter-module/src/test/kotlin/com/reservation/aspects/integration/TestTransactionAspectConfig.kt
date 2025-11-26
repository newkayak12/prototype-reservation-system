package com.reservation.aspects.integration

import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.interceptor.TransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor

@TestConfiguration
@EnableTransactionManagement
class TestTransactionAspectConfig {
    @Bean
    fun mockTransactionManager(): PlatformTransactionManager =
        mockk<PlatformTransactionManager>(relaxed = true)

    @Bean
    fun mockTransactionAttributeSource(): TransactionAttributeSource =
        mockk<TransactionAttributeSource>(relaxed = true)

    @Bean
    fun mockTransactionInterceptor(
        mockTransactionManager: TransactionManager,
        mockTransactionAttributeSource: TransactionAttributeSource,
    ): TransactionInterceptor =
        TransactionInterceptor(mockTransactionManager, mockTransactionAttributeSource)
}
