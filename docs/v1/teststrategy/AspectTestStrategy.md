# Aspect 통합 테스트 전략

## 테스트 목표
- DistributedLockAspect, RateLimiterAspect의 실행 순서 검증
- Spring Boot Test를 사용하지 않고 순수 단위 테스트로 Aspect 동작 검증
- ProxyFactory를 사용한 Aspect Proxy 생성 및 실행 순서 테스트

## 예상 실행 순서
```
DistributedLockAspect (@Order(HIGHEST_PRECEDENCE))
↓
@Transactional (기본 Order) - TransactionInterceptor
↓  
RateLimiterAspect (@Order(LOWEST_PRECEDENCE))
↓
비즈니스 로직
```

## 테스트 환경 구성 전략

### 1. ProxyFactory 기반 Aspect 적용

#### 방법 1: TransactionInterceptor 포함 (완전한 통합 테스트)
```kotlin
// Spring의 ProxyFactory를 사용하여 모든 Aspect를 적용
val proxyFactory = ProxyFactory().apply {
    setTarget(AspectIntegrationTestService())
    
    // 1. DistributedLockAspect (HIGHEST_PRECEDENCE)
    addAdvice(distributedLockAspect)
    
    // 2. TransactionInterceptor (기본 Order)
    val transactionInterceptor = TransactionInterceptor(mockTransactionManager, mockTransactionAttributeSource)
    addAdvice(transactionInterceptor)
    
    // 3. RateLimiterAspect (LOWEST_PRECEDENCE) 
    addAdvice(rateLimiterAspect)
    
    isProxyTargetClass = true // CGLIB 프록시 사용
}
val proxy = proxyFactory.proxy as AspectIntegrationTestService
```

#### 방법 2: @Transactional 제외 (순수 Aspect 순서 테스트)
```kotlin
// DistributedLock과 RateLimiter만 테스트 (더 간단한 접근)
val proxyFactory = ProxyFactory().apply {
    setTarget(AspectIntegrationTestService())
    addAdvice(distributedLockAspect)
    addAdvice(rateLimiterAspect)
    // @Transactional은 테스트에서 제외
}
val proxy = proxyFactory.proxy as AspectIntegrationTestService
```

### 2. Mock 기반 의존성 처리

#### TransactionInterceptor 포함 시 필요한 Mock들
```kotlin
// TransactionManager Mock
val mockTransactionManager: PlatformTransactionManager = mockk {
    every { getTransaction(any()) } returns mockk<TransactionStatus>()
    every { commit(any()) } returns Unit
    every { rollback(any()) } returns Unit
}

// TransactionAttributeSource Mock  
val mockTransactionAttributeSource: TransactionAttributeSource = mockk {
    every { getTransactionAttribute(any(), any()) } returns DefaultTransactionAttribute()
}
```

#### 기타 의존성 처리
- **Redis 관련 의존성**: MockK로 모든 Redis 연산 Mock 처리
- **SpelParser**: 실제 구현체 사용 또는 Mock으로 키 파싱 결과 제공
- **실행 순서 추적**: Mock의 answers 블록에서 순서 기록

### 3. 실행 순서 추적 방법
```kotlin
// 실행 순서를 추적하기 위한 컬렉터
val executionOrder = mutableListOf<String>()

// 각 Aspect에서 실행 시점을 기록
every { mockDistributedLockAdapter.tryLock(any(), any(), any()) } answers {
    executionOrder.add("DistributedLock-Before")
    true // 성공적인 락 획득 시뮬레이션
}

every { mockRateLimiterTemplate.tryAcquire(any(), any(), any(), any()) } answers {
    executionOrder.add("RateLimiter-Before") 
    true // 성공적인 Rate Limit 획득 시뮬레이션
}
```

## 테스트 시나리오

### 시나리오 1: 정상적인 실행 순서 검증
```kotlin
@Test
fun shouldExecuteAspectsInCorrectOrder() {
    // Given: Mock 설정 및 ProxyFactory로 Aspect 적용
    
    // When: 비즈니스 메소드 호출
    val result = proxy.executeWithAllAspects("test")
    
    // Then: 실행 순서 검증
    assertThat(executionOrder).containsExactly(
        "DistributedLock-Before",
        "RateLimiter-Before", 
        "BusinessLogic",
        "RateLimiter-After",
        "DistributedLock-After"
    )
}
```

### 시나리오 2: DistributedLock 실패 시 후속 Aspect 미실행 검증
```kotlin
@Test  
fun shouldNotExecuteSubsequentAspectsWhenDistributedLockFails() {
    // Given: DistributedLock 실패 Mock 설정
    every { mockDistributedLockAdapter.tryLock(any(), any(), any()) } returns false
    
    // When & Then: 예외 발생 및 후속 Aspect 미실행 검증
    assertThrows<TooManyRequestHasBeenComeSimultaneouslyException> {
        proxy.executeWithAllAspects("test")
    }
    
    // RateLimiter는 실행되지 않았어야 함
    verify(exactly = 0) { mockRateLimiterTemplate.tryAcquire(any(), any(), any(), any()) }
}
```

### 시나리오 3: RateLimiter 실패 시 DistributedLock 해제 검증
```kotlin
@Test
fun shouldReleaseDistributedLockWhenRateLimiterFails() {
    // Given: RateLimiter 실패 Mock 설정
    every { mockRateLimiterTemplate.tryAcquire(any(), any(), any(), any()) } returns false
    
    // When & Then: 예외 발생 및 락 해제 검증
    assertThrows<TooManyCreateTimeTableOccupancyRequestException> {
        proxy.executeWithAllAspects("test")
    }
    
    // DistributedLock이 정상적으로 해제되었는지 검증
    verify { mockUnlockAdapter.unlock(any()) }
}
```

## 구현 상세

### 1. 테스트 설정 클래스
```kotlin
@TestConfiguration
class AspectTestConfig {
    
    @Bean
    @Primary
    fun mockDistributedLockAspect(): DistributedLockAspect = mockk()
    
    @Bean  
    @Primary
    fun mockRateLimiterAspect(): RateLimiterAspect = mockk()
    
    // 기타 Mock Bean들...
}
```

### 2. ProxyFactory 설정 헬퍼
```kotlin
class AspectProxyFactory {
    
    fun createProxy(
        target: Any,
        vararg aspects: Any
    ): Any {
        val proxyFactory = ProxyFactory().apply {
            setTarget(target)
            aspects.forEach { addAdvice(it) }
            isProxyTargetClass = true // CGLIB 프록시 사용
        }
        return proxyFactory.proxy
    }
}
```

### 3. 실행 순서 추적 유틸리티
```kotlin
class ExecutionOrderTracker {
    private val executionOrder = mutableListOf<String>()
    
    fun record(step: String) {
        executionOrder.add("${System.currentTimeMillis()}-$step")
    }
    
    fun getOrder(): List<String> = executionOrder.map { 
        it.substringAfter("-") 
    }
    
    fun clear() = executionOrder.clear()
}
```

## 장점

1. **Spring 컨테이너 불필요**: 빠른 테스트 실행
2. **격리된 테스트**: 외부 의존성(Redis, DB) 없이 순수 로직 테스트  
3. **정확한 순서 검증**: ProxyFactory의 Advisor 순서로 실행 순서 보장
4. **Mock을 통한 제어**: 각 단계별 성공/실패 시나리오 정확히 제어 가능

## 주의사항

1. **@Order 애노테이션**: ProxyFactory에서 Advisor 추가 순서가 실행 순서를 결정
2. **CGLib vs JDK Proxy**: 인터페이스 기반이 아닌 클래스 기반 프록시 필요시 CGLIB 설정
3. **SpEL 파싱**: 실제 메소드 파라미터 파싱이 필요한 경우 SpelParser Mock 설정 필요