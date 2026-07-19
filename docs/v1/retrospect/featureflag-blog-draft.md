# 실전 Feature Flag 구현기: 개발자가 반드시 고민해야 하는 4가지 포인트

## 들어가며

Feature Flag를 도입하자는 제안을 받았을 때 솔직히 조금 당황했다. "그냥 if-else로 하면 되는거 아니야?"라는 생각이 먼저 들었기 때문이다. 하지만 막상 구현해보니 생각보다 고려할 점이 많았고, 특히 운영환경에서 안정적으로 동작시키기 위해 많은 고민이 필요했다.

이번 글에서는 **예약 시스템 프로젝트**에서 Feature Flag를 구현하면서 실제로 마주한 4가지 핵심 고민들을 공유하려고 한다. Spring Boot 3.4.5 + Kotlin 환경에서 헥사고날 아키텍처를 적용한 실제 구현 과정의 "왜 이렇게 했는지", "다른 방법은 없었는지"에 대한 솔직한 경험담을 담았다.

## 1. 왜 Feature Flag를 고민했는가?

### 문제의 시작: 예약 확정 로직의 점진적 개선

우리 예약 시스템에서 **예약 확정 프로세스**를 대폭 개선하게 되었다. 기존에는 단순한 예약 승인 방식이었지만, 새로운 요구사항으로 **시간대별 동적 가격 책정**, **자동 대기열 관리**, **실시간 테이블 상태 업데이트** 등 복잡한 비즈니스 로직이 추가되었다.

문제는 이런 핵심 기능을 한 번에 모든 고객에게 적용하기에는 **위험부담이 너무 크다**는 것이었다.

처음에는 단순하게 생각했다:

```kotlin
fun processPayment() {
    if (useNewPaymentSystem) {
        // 새로운 결제 시스템
    } else {
        // 기존 결제 시스템
    }
}
```

하지만 이런 방식의 문제는 곧 드러났다:

- **코드 곳곳에 흩어진 분기 로직**: 결제 관련 메서드마다 동일한 if-else 반복
- **운영 중 설정 변경의 어려움**: 코드 수정 없이 기능을 켜고 끄기 힘듦
- **일관성 없는 처리**: 개발자마다 다른 방식으로 분기 처리

### 현실적인 요구사항들

실제 운영을 고려하니 더 복잡한 요구사항들이 나왔다:

1. **즉시 전환 가능**: 문제 발생 시 1분 내에 기존 시스템으로 전환
2. **로그 추적 가능**: 어떤 기능이 언제 사용되었는지 확인
3. **개발 편의성**: 개발자가 매번 분기 로직을 신경 쓰지 않아도 됨
4. **테스트 용이성**: 기능별로 독립적인 테스트 가능

이런 요구사항들을 보면서 "Feature Flag가 정말 필요하구나"를 실감했다.

## 2. 어떻게 동작하게 할 것인가?

### AOP vs 직접 호출: 개발자 경험을 고민하다

가장 먼저 고민한 것은 **어떤 방식으로 사용하게 할 것인가**였다.

#### 방법 1: 직접 호출 방식

```kotlin
@Service
class PaymentService(
    private val featureFlagService: FeatureFlagService
) {
    fun processPayment() {
        if (featureFlagService.isEnabled("NEW_PAYMENT_SYSTEM")) {
            // 새로운 결제 시스템
        } else {
            // 기존 결제 시스템
        }
    }
}
```

**장점**: 명시적이고 이해하기 쉬움  
**단점**: 비즈니스 로직과 Feature Flag 로직이 섞임, 매번 직접 호출해야 함

#### 방법 2: AOP 기반 어노테이션 방식

```kotlin
@Service
class PaymentService {
    @FeatureFlag(
        featureFlagKey = "NEW_PAYMENT_SYSTEM",
        fallback = "processPaymentLegacy"
    )
    fun processPayment() {
        // 새로운 결제 시스템
    }

    fun processPaymentLegacy() {
        // 기존 결제 시스템
    }
}
```

**장점**: 비즈니스 로직과 분리, 선언적 사용  
**단점**: 런타임 오버헤드, 디버깅 복잡성

### 최종 선택: AOP 기반 방식을 선택한 이유

결국 AOP 방식을 선택했다. 이유는 **개발자 경험(DX)**을 우선시했기 때문이다.

실제 구현한 Aspect는 다음과 같다:

```kotlin
@Aspect
@Component
class FeatureFlagAspect(
    private val findFeatureFlagUseCase: FindFeatureFlagUseCase,
) : ApplicationContextAware {

    @Around("@annotation(featureFlag)")
    fun executeFeatureFlagAction(
        proceedingJoinPoint: ProceedingJoinPoint,
        featureFlag: FeatureFlag,
    ): Any? {
        val isEnabled = verifyFeatureFlag(featureFlag)
        return if (isEnabled) {
            // Feature Flag 활성화 → 원래 메서드 실행
            proceedingJoinPoint.proceed()
        } else {
            // Feature Flag 비활성화 → fallback 메서드 실행
            failOver(proceedingJoinPoint, featureFlag)
        }
    }

    private fun failOver(joinPoint: ProceedingJoinPoint, annotation: FeatureFlag): Any? {
        if (annotation.fallback.isNullOrBlank()) throw AccessNotPermittedException()

        // 리플렉션을 사용한 fallback 메서드 호출
        val targetClass = getClass(joinPoint, annotation)
        val fallbackMethod = getFallbackMethod(targetClass, joinPoint, annotation)
        return fallbackMethod.invoke(joinPoint.target, *joinPoint.args)
    }
}
```

### 3단계 명확한 동작 정의

Feature Flag가 비활성화되었을 때의 동작을 명확히 정의했다:

1. **Feature Flag 활성화**: 원래 메서드 실행
2. **Feature Flag 비활성화 + fallback 존재**: fallback 메서드 실행
3. **Feature Flag 비활성화 + fallback 없음**: `AccessNotPermittedException` 발생

이렇게 명확히 정의한 이유는 **의도하지 않은 동작을 원천 차단**하기 위해서였다.

## 3. 반복 호출에 따른 캐싱과 실패 시 전략

### Cache-aside 패턴 선택의 배경

Feature Flag는 **모든 API 호출마다 조회**되는 고빈도 데이터다. 매번 DB 조회를 한다면 성능상 문제가 될 수 밖에 없었다.

초기에는 Spring의 `@Cacheable`을 고려했지만, **장애 상황별로 다른 정책을 적용**하고 싶어서 직접 구현하기로 했다.

```kotlin
@UseCase
class FindFeatureFlagService(
    private val fetchFeatureFlagTemplate: FindFeatureFlag, // Redis
    private val findFeatureFlagRepository: FindFeatureFlag, // DB
    private val saveFeatureFlagTemplate: SaveFeatureFlag,   // Redis 저장
) : FindFeatureFlagUseCase {

    override fun execute(request: FindFeatureFlagQuery): FindFeatureFlagQueryResult =
        fetchFromRedis(request)

    private fun fetchFromRedis(request: FindFeatureFlagQuery): FindFeatureFlagQueryResult {
        return fetchFeatureFlagTemplate.query(request.toInquiry())
            ?.let { it.toQuery() }
            ?: fetchFromDatabaseAndSaveAtRedis(request) // Cache miss 시 DB 조회 후 Redis 저장
    }
}
```

### setIfAbsent로 Cache Stampede 방지

다중 노드 환경에서 동시에 캐시 미스가 발생하면 **모든 노드가 DB에 동시 접근**하는 문제가 있었다.

Redis의 `setIfAbsent` 원자적 연산을 활용해 이 문제를 해결했다:

```kotlin
@Component
class SaveFeatureFlagTemplate(
    private val featureFlagRedisTemplate: RedisTemplate<String, FeatureFlagRedisEntity>,
) : SaveFeatureFlag {

    override fun command(inquiry: SaveFeatureFlagInquiry): Boolean {
        val opsForValue = featureFlagRedisTemplate.opsForValue()
        val key = inquiry.toKey()
        val value = inquiry.toValue()
        return opsForValue.setIfAbsent(key, value, DURATION_MINUTES, MINUTES)
        // 첫 번째 노드만 저장 성공, 나머지는 실패 → 중복 저장 방지
    }
}
```

이 방법의 장점은 **복잡한 분산락 없이도** Cache Stampede를 효과적으로 방지할 수 있다는 것이었다.

### Retry 정책: Redis 특화 전략

네트워크 장애는 언제든 발생할 수 있다. 하지만 Redis와 DB의 특성이 다르므로 각각에 맞는 전략이 필요했다.

```kotlin
@Retryable(
    retryFor = [InvalidRedisStatusException::class],
    maxAttempts = 5,  // Redis만 5회 재시도
    backoff = Backoff(delay = 100, multiplier = 2.0, maxDelay = 2000)
)
override fun execute(request: FindFeatureFlagQuery): FindFeatureFlagQueryResult =
    fetchFromRedis(request)

@Recover  // Redis 5회 실패 시 DB로 즉시 전환
fun executeWithDatabase(
    exception: InvalidRedisStatusException,
    request: FindFeatureFlagQuery
): FindFeatureFlagQueryResult = fetchFromDatabase(request)
```

**전략 포인트**:

- **Redis**: 빠른 복구 가능성이 있으므로 5회 재시도
- **DB**: Redis 실패 시에만 사용하는 최후의 수단이므로 재시도 없이 단순 전환

## 4. Resilience4J vs. Spring-Retry

### 초기 계획: Resilience4j CircuitBreaker

처음에는 **Resilience4j CircuitBreaker**를 사용하려고 했다. 마이크로서비스에서 자주 사용되는 패턴이고, 장애 전파를 차단할 수 있어서 좋아 보였다.

#### 고려했던 이유들

1. **카스케이딩 실패 방지**: Redis 장애 시 모든 요청이 DB로 몰리는 상황 방지
2. **자동 복구**: Half-Open 상태에서 Redis 복구 감지 후 자동 전환
3. **운영팀 요청**: "Redis 다운 시 DB 부하를 자동으로 차단할 방법이 있을까요?"

#### 초기 설계안
```kotlin
@CircuitBreaker(name = "featureFlag", fallbackMethod = "findFeatureFlagFallback")
@Retry(name = "featureFlag")
fun findFeatureFlag(): FeatureFlagResult {
    // Redis 조회 후 DB fallback
}
```

### 현실과의 괴리: 과연 필요한가?

하지만 구현을 진행하면서 의문이 들었다:

#### 1. Feature Flag의 특수성
```kotlin
// 실제 측정 결과
Redis 조회: 평균 3-5ms (내부 네트워크)
MySQL 조회: 평균 15-25ms (내부 네트워크)
외부 API: 평균 200-500ms (인터넷)
```
Circuit Breaker는 주로 **불안정한 외부 API**를 위한 패턴인데, Redis/MySQL은 **안정적인 내부 인프라**였다.

#### 2. "실패" 정의의 모호성
```kotlin
// 이런 경우들을 어떻게 처리할 것인가?
- Redis에 Feature Flag가 없는 경우 → 정상? 실패?
- 새로운 Feature Flag 추가 시 캐시 미스 → 정상? 실패?
- Redis 응답 지연(10ms) → 정상? 실패?
```
**새로운 Feature Flag는 당연히 캐시에 없는데**, 이를 "실패"로 인식하면 불필요하게 Circuit이 열릴 위험이 있었다.

#### 3. 복구 지연 문제
**시뮬레이션 결과**:
- Redis 5초 장애 → 실패율 60% → Circuit Open 
- Redis 복구되어도 **wait-duration(30s) 동안 DB 우회**
- 빠른 복구가 중요한 Feature Flag에서는 **복구 지연**이 역효과

#### 4. 복잡성 대비 효과
단순한 Feature Flag 조회를 위해 추가로 모니터링해야 할 지표들:
```yaml
- circuitbreaker.state (OPEN/CLOSED/HALF_OPEN)
- circuitbreaker.failure_rate
- circuitbreaker.slow_call_rate
- retry.attempts
```

### 최종 선택: Spring Retry만 사용

결국 **Spring Retry + 단순한 Fallback 패턴**을 선택했다.

```kotlin
@Retryable(
    retryFor = [InvalidRedisStatusException::class],
    maxAttempts = 5,
    backoff = Backoff(delay = 100, multiplier = 2.0, maxDelay = 2000)
)
override fun execute(request: FindFeatureFlagQuery): FindFeatureFlagQueryResult =
    fetchFromRedis(request)

@Recover  // Redis 5회 실패 시 DB로 즉시 전환
fun executeWithDatabase(
    exception: InvalidRedisStatusException,
    request: FindFeatureFlagQuery
): FindFeatureFlagQueryResult = fetchFromDatabase(request)
```

#### 선택한 이유들

**1. 적절한 수준의 복잡성**
- Feature Flag 조회라는 **단순한 작업**에는 단순한 재시도와 fallback이면 충분
- 과도한 엔지니어링(Over-engineering) 방지

**2. 명확하고 예측 가능한 동작**
```
Redis 조회 → 5회 재시도 → 실패 시 DB 조회 → 그래도 실패 시 기본값
```
복잡한 상태 머신 없이 **선형적인 fallback chain**으로 누구나 쉽게 이해할 수 있다.

**3. 빠른 장애 복구**
- Redis 장애 시 **즉시 DB로 전환** (Circuit Breaker의 wait-duration 없음)
- 복구 시에도 즉시 Redis 사용 재개

**4. 낮은 학습 비용**
- Spring Retry는 대부분의 Spring 개발자가 이미 알고 있는 기술
- 새로운 팀원도 **5분 내에 이해 가능**

### 핵심 깨달음

**Resilience4j를 사용하지 않은 핵심 이유**:
1. **내부 인프라의 안정성**: Redis/MySQL 장애 빈도가 낮아 Circuit Breaker 이점 제한적
2. **빠른 복구 우선**: wait-duration이 오히려 복구를 지연시킴
3. **적정 복잡성**: Feature Flag 조회에는 단순한 재시도면 충분
4. **예측 가능성**: 복잡한 상태 변화보다 선형적 fallback이 더 안전

**"좋은 기술"이 아니라 "지금 우리에게 맞는 기술"을 선택**하는 것이 더 중요하다는 교훈을 얻었다.

## 마치며

Feature Flag 구현은 단순한 boolean 값 확인이 아니라 **시스템 전체의 안정성과 개발자 경험을 고려하는 종합적인 작업**이었다.

### 핵심 교훈들

1. **개발자 경험 우선**: 약간의 성능 오버헤드보다는 사용하기 쉬운 API가 팀 생산성에 더 중요
2. **기술의 특성 활용**: Redis `setIfAbsent` 같은 기본 기능을 활용하면 복잡한 솔루션 없이도 문제 해결 가능
3. **적정 기술 선택**: 멋진 도구가 있다고 해서 무조건 사용할 필요는 없음, 문제에 맞는 적절한 수준의 해결책 선택
4. **명확한 동작 정의**: 모호함보다는 명확함이 운영 환경에서의 예측 가능성을 높임

### 개선할 점들

물론 아직 개선할 점들도 있다:

- **동적 설정 지원**: 서버 재시작 없이 Feature Flag 값 변경
- **점진적 롤아웃**: 사용자 그룹별, 퍼센트별 제어
- **모니터링 강화**: 사용률 및 성능 지표 수집

하지만 현재 구현만으로도 **안정적인 점진적 배포**라는 핵심 목표는 달성할 수 있었다.

가장 중요한 것은 **과도한 엔지니어링을 피하고 문제의 본질에 집중**하는 것이었다. 복잡하고 멋진 기술을 사용하는 것보다, 팀의 상황에 맞는 적절한 수준의 솔루션을 만드는 것이 더 가치 있다는 걸 다시 한번
깨달았다.