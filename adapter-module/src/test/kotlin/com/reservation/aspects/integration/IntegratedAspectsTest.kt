package com.reservation.aspects.integration

import com.reservation.aspects.integration.IntegratedAspectsTest.IntegratedAspectConfig
import com.reservation.config.aspect.DistributedLockAspect
import com.reservation.config.aspect.SpelParser
import com.reservation.redis.redisson.lock.AcquireLockTemplate
import com.reservation.redis.redisson.lock.CheckLockTemplate
import com.reservation.redis.redisson.lock.UnlockLockTemplate
import com.reservation.timetable.exceptions.TooManyRequestHasBeenComeSimultaneouslyException
import io.kotest.assertions.assertSoftly
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor
import org.springframework.transaction.interceptor.TransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor

/**
 * IntegratedAspectsTest - Aspect 실행 순서 및 통합 테스트
 *
 * ## 테스트 목적
 * - DistributedLockAspect,  @Transactional 의 실행 순서 검증
 * - 각 Aspect 가 올바르게 동작하는지 통합적으로 검증
 * - 예상 실행 순서: DistributedLockAspect → @Transactional
 *
 * ## Aspect 실행 순서 분석
 * 1. DistributedLockAspect (@Order(Ordered.HIGHEST_PRECEDENCE) = Integer.MIN_VALUE)
 *    - 가장 먼저 실행됨
 *    - Redis 분산 락을 통해 동시성 제어
 *    - 락 획득 실패시 TooManyRequestHasBeenComeSimultaneouslyException 발생
 *
 * 2. @Transactional (기본 Order = Integer.MAX_VALUE)
 *    - 두 번째로 실행됨
 *    - 데이터베이스 트랜잭션 경계 설정
 *    - 예외 발생시 롤백 처리
 *
 * ## 테스트 시나리오
 *
 * ### 시나리오 1: 정상적인 실행 흐름 테스트
 * - 모든 Aspect 가 정상적으로 동작하는 경우
 * - 실행 순서: DistributedLock → @Transactional →  비즈니스 로직
 * - 예상 결과: 모든 단계가 성공적으로 완료
 *
 * ### 시나리오 2: DistributedLock 실패 테스트
 * - 분산 락 획득에 실패하는 경우 (동시 요청)
 * - 예상 결과: TooManyRequestHasBeenComeSimultaneouslyException 발생
 * - 후속 Aspect 들은 실행되지 않음
 *
 * ### 시나리오 3: 비즈니스 로직 실패 테스트
 * - 비즈니스 로직에서 예외가 발생하는 경우
 * - 예상 결과: @Transactional 롤백 처리
 * - DistributedLock 정상 해제 확인
 *
 * ## 테스트 구현 계획
 *
 * ### 1. 테스트용 서비스 클래스 생성
 * ```kotlin
 * @Service
 * class AspectIntegrationTestService {
 *     @DistributedLock(
 *         key = "'test:lock:' + #param1",
 *         lockType = LockType.LOCK,
 *         waitTime = 1,
 *         waitTimeUnit = TimeUnit.SECONDS
 *     )
 *     @Transactional
 *     fun executeWithAllAspects(param1: String): String {
 *         // 테스트용 비즈니스 로직
 *         return "success-$param1"
 *     }
 * }
 * ```
 *
 * ### 2. Aspect 실행 순서 추적을 위한 Mock/Spy 설정
 * - MockK 를 사용하여 각 Aspect 의 실행 순서를 추적
 * - 실행 시점을 기록하여 순서 검증
 *
 * ### 3. Redis/Database 설정
 * - Testcontainers 를 사용한 Redis/MySQL 컨테이너 설정
 * - 각 테스트 후 데이터 정리
 *
 * ### 4. 동시성 테스트
 * - CompletableFuture 또는 Kotlin Coroutines 를 사용하여 동시 요청 시뮬레이션
 * - 분산 락의 동시성 제어 검증
 *
 * ### 5. 예외 처리 테스트
 * - 각 단계에서 발생할 수 있는 예외 상황 검증
 * - 예외 발생시 리소스 정리 확인 (락 해제, 트랜잭션 롤백)
 *
 * ## 검증 포인트
 * 1. Aspect 실행 순서가 올바른가?
 * 2. 각 Aspect 가 올바르게 동작하는가?
 * 3. 예외 발생시 리소스 정리가 올바르게 이루어지는가?
 * 4. 동시성 제어가 올바르게 동작하는가?
 * 5. 트랜잭션 경계가 올바르게 설정되는가?
 */
@Suppress("LongParameterList")
@ExtendWith(value = [SpringExtension::class, MockKExtension::class])
@ContextConfiguration(
    classes = [
        TestCommonConfig::class,
        TestDistributedLockAspectConfig::class,
        TestTransactionAspectConfig::class,
        IntegratedAspectConfig::class,
    ],
)
class IntegratedAspectsTest {
    @TestConfiguration
    @EnableAspectJAutoProxy
    class IntegratedAspectConfig {
        @Bean
        fun distributedLockAspect(
            acquireFairLockAdapter: AcquireLockTemplate,
            checkFairLockAdapter: CheckLockTemplate,
            unlockFairLockAdapter: UnlockLockTemplate,
            acquireLockAdapter: AcquireLockTemplate,
            checkLockAdapter: CheckLockTemplate,
            unlockLockAdapter: UnlockLockTemplate,
            spelParser: SpelParser,
        ) = DistributedLockAspect(
            acquireFairLockAdapter,
            checkFairLockAdapter,
            unlockFairLockAdapter,
            acquireLockAdapter,
            checkLockAdapter,
            unlockLockAdapter,
            spelParser,
        )

        @Bean
        fun transactionAdvisor(
            mockTransactionAttributeSource: TransactionAttributeSource,
            mockTransactionInterceptor: TransactionInterceptor,
        ): BeanFactoryTransactionAttributeSourceAdvisor {
            val advisor = BeanFactoryTransactionAttributeSourceAdvisor()
            advisor.setTransactionAttributeSource(mockTransactionAttributeSource)
            advisor.advice = mockTransactionInterceptor
            return advisor
        }

        @Bean
        fun aspectIntegrationTestDomainService(): AspectIntegrationTestDomainService =
            mockk(relaxed = true)

        @Bean
        fun aspectIntegrationTestService(
            aspectIntegrationTestDomainService: AspectIntegrationTestDomainService,
        ): AspectIntegrationTestService {
            return AspectIntegrationTestService(aspectIntegrationTestDomainService)
        }
    }

    @BeforeEach
    fun reset() {
        clearAllMocks()
    }

    @Autowired
    lateinit var acquireLockAdapter: AcquireLockTemplate

    @Autowired
    lateinit var checkLockAdapter: CheckLockTemplate

    @Autowired
    lateinit var unlockLockAdapter: UnlockLockTemplate

    @Autowired
    lateinit var mockTransactionManager: PlatformTransactionManager

    @Autowired
    lateinit var aspectIntegrationTestDomainService: AspectIntegrationTestDomainService

    @Autowired
    lateinit var service: AspectIntegrationTestService

    @Test
    fun contextLoads() {
        val targets =
            listOf(
                AopUtils.isAopProxy(service),
                AopUtils.isJdkDynamicProxy(service),
                AopUtils.isCglibProxy(service),
            )

        assertSoftly {
            assertThat(targets).anyMatch { it }
        }
    }

    /**
     *     ### 시나리오 1: 정상적인 실행 흐름 테스트
     *     - 모든 Aspect 가 정상적으로 동작하는 경우
     *     - 실행 순서: DistributedLock → @Transactional → 비즈니스 로직
     *     - 예상 결과: 모든 단계가 성공적으로 완료
     */
    @DisplayName("시나리오 1: 정상적인 실행 흐름 테스트")
    @Test
    fun scenario1() {
        val executionOrder = mutableListOf<String>()
        val parameter = "test"

        every {
            acquireLockAdapter.tryLock(any(), any(), any())
        } answers {
            executionOrder.add("DistributedLock-Lock")
            true
        }
        every {
            mockTransactionManager.getTransaction(any())
        } answers {
            executionOrder.add("Transaction-Begin")
            mockk<TransactionStatus>()
        }

        every {
            aspectIntegrationTestDomainService.decorateString(any())
        } answers {
            executionOrder.add("DomainService-Begin")
            "aspect test :: $parameter"
        }

        every { mockTransactionManager.rollback(any()) } answers {
            executionOrder.add("Transaction-Rollback")
            Unit
        }

        every { mockTransactionManager.commit(any()) } answers {
            executionOrder.add("Transaction-Commit")
            Unit
        }

        every { checkLockAdapter.isHeldByCurrentThread(any()) } returns true
        every { unlockLockAdapter.unlock(any()) } answers {
            executionOrder.add("DistributedLock-Unlock")
            Unit
        }

        val result = service.aspectLayerTest(parameter)

        assertThat(result).contains("test")
        assertThat(executionOrder).containsExactly(
            "DistributedLock-Lock",
            "Transaction-Begin",
            "DomainService-Begin",
            "Transaction-Commit",
            "DistributedLock-Unlock",
        )
    }

    /**
     *     ### 시나리오 2: DistributedLock 실패 테스트
     *     - 분산 락 획득에 실패하는 경우 (동시 요청)
     *     - 예상 결과: TooManyRequestHasBeenComeSimultaneouslyException 발생
     *     - 후속 Aspect 들은 실행되지 않음
     */
    @DisplayName("시나리오 2: DistributedLock 실패 테스트")
    @Test
    fun scenario2() {
        val executionOrder = mutableListOf<String>()
        val parameter = "test"

        every {
            acquireLockAdapter.tryLock(any(), any(), any())
        } answers {
            executionOrder.add("DistributedLock-Lock")
            false
        }
        every {
            mockTransactionManager.getTransaction(any())
        } answers {
            executionOrder.add("Transaction-Begin")
            mockk<TransactionStatus>()
        }

        every {
            aspectIntegrationTestDomainService.decorateString(any())
        } answers {
            executionOrder.add("DomainService-Begin")
            "aspect test :: $parameter"
        }

        every { mockTransactionManager.rollback(any()) } answers {
            executionOrder.add("Transaction-Rollback")
            Unit
        }

        every { mockTransactionManager.commit(any()) } answers {
            executionOrder.add("Transaction-Commit")
            Unit
        }

        every { checkLockAdapter.isHeldByCurrentThread(any()) } returns true
        every { unlockLockAdapter.unlock(any()) } answers {
            executionOrder.add("DistributedLock-Unlock")
            Unit
        }

        assertThrows<TooManyRequestHasBeenComeSimultaneouslyException> {
            service.aspectLayerTest(parameter)
        }
        assertThat(executionOrder).containsExactly(
            "DistributedLock-Lock",
        )
    }

    /**
     *     ### 시나리오 3: 비즈니스 로직 실패 테스트
     *     - 비즈니스 로직에서 예외가 발생하는 경우
     *     - 예상 결과: @Transactional 롤백 처리
     *     - DistributedLock 정상 해제 확인
     */

    @DisplayName("시나리오 3: 비즈니스 로직 실패 테스트")
    @Test
    fun scenario4() {
        val executionOrder = mutableListOf<String>()
        val parameter = "test"

        every {
            acquireLockAdapter.tryLock(any(), any(), any())
        } answers {
            executionOrder.add("DistributedLock-Lock")
            true
        }
        every {
            mockTransactionManager.getTransaction(any())
        } answers {
            executionOrder.add("Transaction-Begin")
            mockk<TransactionStatus>()
        }

        every {
            aspectIntegrationTestDomainService.decorateString(any())
        } throws RuntimeException()

        every { mockTransactionManager.rollback(any()) } answers {
            executionOrder.add("Transaction-Rollback")
            Unit
        }

        every { mockTransactionManager.commit(any()) } answers {
            executionOrder.add("Transaction-Commit")
            Unit
        }

        every { checkLockAdapter.isHeldByCurrentThread(any()) } returns true
        every { unlockLockAdapter.unlock(any()) } answers {
            executionOrder.add("DistributedLock-Unlock")
            Unit
        }

        assertThrows<Exception> {
            service.aspectLayerTest(parameter)
        }

        assertThat(executionOrder).containsExactly(
            "DistributedLock-Lock",
            "Transaction-Begin",
            "Transaction-Rollback",
            "DistributedLock-Unlock",
        )
    }
}
