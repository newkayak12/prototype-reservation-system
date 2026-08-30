# Distributed Lock을 적용하면서

## 목차
### 1. 예약 시스템에서 마주한 동시성 문제
### 2. @Transactional만으로는 해결되지 않는 이유
### 3. Redisson + AOP로 해결하기
### 4. 일반 락과 Fair 락, 어떤 걸 써야 할까?
### 5. ThreadLocal로 락 인스턴스 관리하기
### 6. 실제 적용 과정에서 배운 것들
### 7. 성능과 모니터링에 대한 고민

---

## 1. 예약 시스템에서 마주한 동시성 문제

### 문제 상황
```kotlin
// 레스토랑 A, 12월 10일 19:00 예약
// 동시에 3명의 사용자가 예약 버튼을 클릭하면?

@Transactional
fun occupyTimeTable(command: CreateTimeTableOccupancyCommand): Boolean {
    val timeTables = findAvailableTimeTables(command.restaurantId, command.date, command.startTime)
    // 💥 3명이 동시에 이 시점을 지나가면 모두 같은 테이블을 보게 된다
    if (timeTables.isEmpty()) throw AllSeatsOccupiedException()
    
    return saveOccupancy(timeTables[0], command.userId)
}
```

실제로는 1개의 테이블만 남아있는데, 3명이 동시에 진입하면 **모두가 "테이블이 있다"고 인식**하게 된다.

### 트랜잭션만으로 안 되는 이유

- 예약 로직이 단순히 DB만 건드리는게 아니었다.
  ```kotlin
  // 실제 예약 로직 
  1. 가용 테이블 조회 (MySQL)
  2. 세마포어 획득 (Redis) 
  3. 예약 처리 (MySQL)
  4. 이벤트 발행 (Outbox)
  5. 외부 시스템 연동 (HTTP)
  ```
  
- DB 트랜잭션 범위에 Redis와 HTTP 호출까지 다 묶기에는 **너무 무거워진다**.
- 그렇다고 부분적으로만 트랜잭션을 걸면 중간에 실패했을 때 **일관성이 깨진다**.

실제 고민했던 방법들:
1. **DB Lock**: `SELECT ... FOR UPDATE` → 성능상 부담, 데드락 위험
2. **Redis Lock**: 직접 구현 → 복잡하고 휴먼 에러 가능성
3. **Queue**: 비동기 처리 → 사용자 응답성 떨어짐

결국 **Redisson 분산 락**으로 가게 되었다.

---

## 2. @Transactional만으로는 해결되지 않는 이유
### Resource Racing은 왜 일어났을까?

실제 예약 시스템에서 겪었던 시나리오:

```
19:00 예약 오픈 → 100명이 동시에 "예약하기" 버튼 클릭
```

1. **100명 모두 동시에 `findAvailableTimeTables()` 호출**
2. **MySQL에서 동일한 시점의 가용 테이블 리스트 반환 (예: [TableA, TableB])**  
3. **100명 모두 "아, 테이블이 2개나 있네!" 라고 생각**
4. **100명 모두 동시에 예약 처리 시도** 
5. **결과: 테이블 2개인데 예약은 100개 생성됨** 💥

### 왜 @Transactional로는 해결이 안 될까?

```kotlin
@Transactional  // 이것만으로는 부족했다
fun occupyTimeTable(command: CreateTimeTableOccupancyCommand): Boolean {
    // 1. 가용 테이블 조회 (MySQL)
    val timeTables = loadBookableTimeTables(command)
    
    // 2. 세마포어 획득 (Redis) - 트랜잭션 범위 밖!
    acquireSemaphore(key, timeTables.size)
    
    // 3. 예약 저장 (MySQL) 
    val domainEvent = saveOccupancy(command.userId, timeTables)
    
    // 4. Outbox 이벤트 저장 (MySQL)
    // 5. 외부 API 호출 (HTTP) - 트랜잭션에 넣으면 너무 무거워짐
    return saveToOutBoxAndPublish(domainEvent)
}
```

**문제점들**:
1. **Redis 세마포어**는 DB 트랜잭션과 별개로 동작
2. **외부 API 호출**을 트랜잭션에 포함시키기엔 너무 느림 (타임아웃 위험)
3. **비즈니스 검증 로직**이 트랜잭션 시작 전에 실행됨

그래서 **전체 예약 프로세스를 하나의 원자적 단위로 보호할 방법**이 필요했다.

---

## 3. Redisson + AOP로 해결하기

### 처음엔 직접 구현하려고 했다

```kotlin
// 처음 시도했던 방식
fun occupyTimeTable(command: CreateTimeTableOccupancyCommand): Boolean {
    val lockKey = "reservation:${command.restaurantId}:${command.date}:${command.startTime}"
    val lock = redissonClient.getLock(lockKey)
    
    if (lock.tryLock(2, TimeUnit.MINUTES)) {
        try {
            // 비즈니스 로직
        } finally {
            lock.unlock()
        }
    } else {
        throw TooManyRequestException()
    }
}
```

**문제점**: 모든 메소드마다 이런 보일러플레이트 코드가 반복됨. 실수하기 쉬움.

### 어노테이션으로 깔끔하게 만들기

```kotlin
@Target(FUNCTION)
@Retention(RUNTIME)
annotation class DistributedLock(
    val key: String,              // SpEL로 동적 키 생성
    val lockType: LockType,       // 일반 락 vs Fair 락
    val waitTime: Long,           
    val waitTimeUnit: TimeUnit,   
)

enum class LockType {
    LOCK,      // 빠름, 순서 보장 없음
    FAIR_LOCK, // 느림, FIFO 순서 보장
}
```

**이제 이렇게 쓸 수 있다**:
```kotlin
@DistributedLock(
    key = "'LOCK:' + #command.restaurantId + ':' + #command.date + ':' + #command.startTime",
    lockType = LockType.FAIR_LOCK,  // 예약은 선착순이니까
    waitTime = 2L,
    waitTimeUnit = TimeUnit.MINUTES,
)
@Transactional
override fun execute(command: CreateTimeTableOccupancyCommand): Boolean {
    // 락 획득/해제는 AOP가 알아서 처리
    // 비즈니스 로직만 신경쓰면 됨
}
```

### Template 패턴으로 락 타입 분리

처음엔 일반 락과 Fair 락을 하나의 클래스에서 처리하려 했다. 그런데 각각의 특성이 달라서 분리하는 게 나았다.

```kotlin
// 공통 인터페이스
interface AcquireLockTemplate {
    fun tryLock(name: String, waitTime: Long, waitTimeUnit: TimeUnit): Boolean
}
interface CheckLockTemplate {
    fun isHeldByCurrentThread(name: String): Boolean
}
interface UnlockLockTemplate {
    fun unlock(name: String)
}
```

```kotlin
// 일반 락: 빠르지만 순서 보장 안됨
@Component
class AcquireLockAdapter(
    private val redissonClient: RedissonClient,
) : AcquireLockTemplate {
    override fun tryLock(name: String, waitTime: Long, waitTimeUnit: TimeUnit): Boolean {
        val lock = LockStore.getOrCreateLock(name) {
            redissonClient.getLock(LockStore.key(name))  
        }
        return lock.tryLock(waitTime, waitTimeUnit)
    }
}

// Fair 락: 느리지만 FIFO 순서 보장
@Component  
class AcquireFairLockAdapter(
    private val redissonClient: RedissonClient,
) : AcquireLockTemplate {
    override fun tryLock(name: String, waitTime: Long, waitTimeUnit: TimeUnit): Boolean {
        val lock = FairLockStore.getOrCreateFairLock(name) {
            redissonClient.getFairLock(FairLockStore.key(name))  // 여기가 다름!
        }
        return lock.tryLock(waitTime, waitTimeUnit)
    }
}
```

---

### AOP로 자동화하기

AOP가 핵심이다. 어노테이션만 붙이면 나머지는 알아서 처리되도록 만들고 싶었다.

```kotlin
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // 다른 AOP보다 먼저 실행
class DistributedLockAspect(
    // 일반 락 어댑터들
    private val acquireLockAdapter: AcquireLockTemplate,
    private val checkLockAdapter: CheckLockTemplate,
    private val unlockLockAdapter: UnlockLockTemplate,
    // Fair 락 어댑터들  
    private val acquireFairLockAdapter: AcquireLockTemplate,
    private val checkFairLockAdapter: CheckLockTemplate,
    private val unlockFairLockAdapter: UnlockLockTemplate,
    private val spelParser: SpelParser,  // 동적 키 생성용
) {
    @Around("@annotation(com.reservation.config.annotations.DistributedLock)")
    fun executeDistributedLockAction(proceedingJoinPoint: ProceedingJoinPoint): Any? {
        val distributedLock = extractAnnotation(proceedingJoinPoint)
        val parsedKey = parseKey(proceedingJoinPoint, distributedLock)
        var shouldRelease = true
        
        try {
            acquireLock(parsedKey, distributedLock)
            return proceedingJoinPoint.proceed()  // 실제 비즈니스 로직 실행
        } catch (e: TooManyRequestHasBeenComeSimultaneouslyException) {
            shouldRelease = false  // 락 못 얻은 경우 해제 시도하지 않음
            throw e
        } finally {
            if (shouldRelease) releaseLock(parsedKey, distributedLock)
        }
    }
}
```

**중요한 부분**:
- `@Order(HIGHEST_PRECEDENCE)`: 다른 AOP(트랜잭션, 캐시 등)보다 먼저 실행되도록
- `shouldRelease = false`: 락을 아예 못 얻은 경우 해제 시도하면 안됨 (다른 스레드 락 실수로 해제할 수 있음)

### SpEL로 동적 키 만들기

처음엔 하드코딩으로 락 키를 만들었는데, 매개변수가 바뀔 때마다 코드를 수정해야 했다. SpEL을 쓰니까 훨씬 유연해졌다.

```kotlin
@Component
class SpelParser {
    private val parser = SpelExpressionParser()
    
    fun parse(expression: String, method: Method, args: Array<Any?>): String {
        val context = StandardEvaluationContext().apply {
            method.parameters.forEachIndexed { index, param ->
                setVariable(param.name, args[index])  // #command 같은 변수 등록
            }
        }
        return parser.parseExpression(expression).getValue(context) as String
    }
}
```

**사용 예시**:
```kotlin
// 이렇게 쓰면
@DistributedLock(
    key = "'LOCK:' + #command.restaurantId + ':' + #command.date + ':' + #command.startTime"
)
fun execute(command: CreateTimeTableOccupancyCommand)

// 실제로는 이런 키가 생성됨
// "LOCK:restaurant_123:2024-12-10:19:00"
```

날짜 포맷팅도 SpEL로 할 수 있어서 편했다:
```kotlin
key = """
    'LOCK:' + #command.restaurantId + ':' +
    #command.date.format(T(java.time.format.DateTimeFormatter).ofPattern('yyyyMMdd')) + ':' +
    #command.startTime.format(T(java.time.format.DateTimeFormatter).ofPattern('HHmm'))
"""
```

### 4.3 실제 사용 예시

```kotlin
@DistributedLock(
    key = """
         'DISTRIBUTED_LOCK:' + #command.restaurantId + ':' +
          #command.date.format(T(java.time.format.DateTimeFormatter).ofPattern('yyyyMMdd')) + ':' +
          #command.startTime.format(T(java.time.format.DateTimeFormatter).ofPattern('HHmm'))
    """,
    lockType = LockType.FAIR_LOCK,
    waitTime = 2L,
    waitTimeUnit = TimeUnit.MINUTES,
)
@Transactional
override fun execute(command: CreateTimeTableOccupancyCommand): Boolean {
    // 예약 비즈니스 로직
    val timeTables = loadBookableTimeTables(command)
    acquireSemaphore(key, timeTables.size)
    val domainEvent = saveOccupancy(command.userId, timeTables)
    return saveToOutBoxAndPublish(domainEvent)
}
```

---

## 4. 일반 락과 Fair 락, 어떤 걸 써야 할까?

### 처음엔 일반 락만 썼다

```kotlin
val lock = redissonClient.getLock("myLock")
if (lock.tryLock(2, TimeUnit.SECONDS)) {
    try {
        // 비즈니스 로직
    } finally {
        lock.unlock()
    }
}
```

**빠르고 간단하다**. 대부분의 상황에서 문제없이 동작한다.

### 그런데 예약에서는 문제가 있었다

예약 시스템에서는 **"선착순"**이 중요하다. 먼저 클릭한 사람이 먼저 예약을 받아야 한다.

그런데 일반 락은 **순서를 보장하지 않는다**. A가 B보다 먼저 락을 요청해도, B가 먼저 락을 얻을 수 있다.

```kotlin
// 이런 상황이 벌어질 수 있음
사용자A: 19:00:00.001에 예약 버튼 클릭
사용자B: 19:00:00.002에 예약 버튼 클릭

// 하지만 B가 먼저 락을 얻어서 예약 성공
// A는 "모든 테이블이 예약되었습니다" 메시지를 받음
```

**불공정하다**. 사용자 경험 측면에서 문제가 생긴다.

### Fair Lock으로 바꿨다

```kotlin
val fairLock = redissonClient.getFairLock("myFairLock") 
if (fairLock.tryLock(2, TimeUnit.SECONDS)) {
    try {
        // FIFO 순서로 처리됨
    } finally {
        fairLock.unlock()
    }
}
```

**Fair Lock의 특징**:
- **FIFO 큐**: 먼저 요청한 순서대로 락 획득
- **공정성 보장**: 선착순 처리가 정확함  
- **성능 비용**: 일반 락보다 느림 (Redis에 큐 관리 오버헤드)

### 언제 어떤 걸 써야 할까?

**내가 내린 결론**:

```kotlin
// 순서가 중요한 경우 → Fair Lock
@DistributedLock(lockType = LockType.FAIR_LOCK, ...)
fun createReservation(command: CreateReservationCommand): Boolean

// 순서는 상관없고 성능이 중요한 경우 → 일반 Lock  
@DistributedLock(lockType = LockType.LOCK, ...)
fun refreshCache(productId: String): Boolean
```

**예약 시스템에서는 무조건 Fair Lock**을 쓰기로 했다. 좀 느려도 공정한 게 더 중요했다.

---

## 5. ThreadLocal로 락 인스턴스 관리하기

### 왜 ThreadLocal을 써야 했을까?

처음엔 단순하게 생각했다:
```kotlin
// 이렇게 하면 되는 거 아닌가?
val lock1 = redissonClient.getLock("myKey")
lock1.lock()
// ... 비즈니스 로직 ...
lock1.unlock()
```

그런데 **문제가 있었다**. 같은 키에 대해 여러 번 락 인스턴스를 만들면:

```kotlin
val lock1 = redissonClient.getLock("myKey")  // 인스턴스 A
val lock2 = redissonClient.getLock("myKey")  // 인스턴스 B (다른 객체!)

lock1.lock()
lock2.unlock()  // 💥 IllegalMonitorStateException!
```

**Redisson의 제약**: lock()과 unlock()은 **같은 인스턴스**로 호출해야 함.

### ThreadLocal로 해결

```kotlin
object LockStore {
    private val LOCK: ThreadLocal<MutableMap<String, RLock>> =
        ThreadLocal.withInitial { mutableMapOf() }
    
    fun getOrCreateLock(name: String, lockProvider: () -> RLock): RLock = 
        LOCK.get().computeIfAbsent(key(name)) { lockProvider() }
        
    fun getLock(name: String): RLock = 
        LOCK.get()[key(name)] ?: throw NoSuchLockException()
}
```

**이점들**:
1. **스레드별 격리**: 다른 스레드의 락과 섞이지 않음
2. **인스턴스 재사용**: 같은 스레드에서 같은 키면 같은 인스턴스 반환
3. **메모리 효율**: 스레드별로 필요한 락만 유지

---

## 6. 실제 적용 과정에서 배운 것들

### 락 키 설계가 생각보다 중요했다

처음엔 간단하게 생각했다:
```kotlin
// 처음 설계: 너무 광범위했음
'DISTRIBUTED_LOCK:restaurant_123'
```

그런데 이렇게 하니까 **문제가 생겼다**. 레스토랑 하나에 락이 걸리면, 같은 레스토랑의 **모든 시간대 예약이 차단**된다.

19시 예약하는 사람 때문에 20시 예약하려는 사람이 기다려야 하는 상황이 벌어졌다. 말이 안 되잖아?

```kotlin
// 개선한 설계: 시간대별로 세분화
'DISTRIBUTED_LOCK:restaurant_123:20241210:1900'  
'DISTRIBUTED_LOCK:restaurant_123:20241210:2000'  // 다른 시간은 별도 락
```

**결과**: 동시 처리량이 **3-4배 증가**했다. 락 경합이 줄어드니까 당연한 결과였다.

### Rate Limiter와의 조합이 생명이었다

처음엔 분산 락만 썼다. 그런데 **락 대기 시간이 너무 길어지는** 문제가 있었다.

```kotlin
@RateLimiter(  // 첫 번째 방어선: 대량 트래픽 차단
    rate = 1000L,                    
    maximumWaitTime = 3L,            
)
@DistributedLock(  // 두 번째 방어선: 정밀한 동시성 제어
    key = "'DISTRIBUTED_LOCK:' + #command.restaurantId + ':' + #command.date.format(T(java.time.format.DateTimeFormatter).ofPattern('yyyyMMdd')) + ':' + #command.startTime.format(T(java.time.format.DateTimeFormatter).ofPattern('HHmm'))",
    lockType = LockType.FAIR_LOCK,
    waitTime = 2L,                   
    waitTimeUnit = TimeUnit.MINUTES,
)
```

**실행 순서**: Rate Limiter → Distributed Lock → Business Logic

이렇게 하니까:
1. **과도한 트래픽**은 Rate Limiter에서 1차 차단
2. **정상 범위 내 동시 요청**은 Fair Lock에서 순서대로 처리
3. 시스템이 **훨씬 안정적**이 되었음

### 예외 처리에서 삽질했던 경험

처음엔 이렇게 짰다:
```kotlin
try {
    acquireLock()
    businessLogic()
} finally {
    releaseLock()  // 💥 이게 문제였다!
}
```

**문제**: 락 획득에 실패해도 finally에서 unlock을 시도했다. 
- 락을 못 얻은 상태에서 unlock 호출 → `IllegalMonitorStateException`
- 더 심각한 건, 다른 스레드의 락을 실수로 해제할 수 있었다

**경험을 통해 깨달은 해결책**:
```kotlin
var shouldRelease = true
try {
    acquireLock()
    return businessLogic()
} catch (e: TooManyRequestHasBeenComeSimultaneouslyException) {
    shouldRelease = false  // 락을 못 얻었으니 해제 시도하지 않음
    throw e
} finally {
    if (shouldRelease) releaseLock()
}
```

이런 디테일들이 실제 운영에서는 정말 중요하다는 걸 배웠다.

### Redis 연결 상태 모니터링의 필요성

분산 락은 **Redis에 완전히 의존**한다. Redis 연결이 끊어지면 락이 제대로 동작하지 않는다.

실제로 Redis 장애가 한 번 있었는데:
1. **락 획득은 실패**하지만 예외도 안 던짐
2. **비즈니스 로직은 그대로 실행**됨  
3. **동시성 제어가 완전히 무력화**됨

그 이후로는 Redis health check를 강화하고, 장애 감지 시 **graceful degradation** 전략을 만들었다.

---

## 7. 성능과 모니터링에 대한 고민

### 타임아웃 설정은 비즈니스 요구사항을 반영해야 한다

처음엔 모든 락에 똑같은 타임아웃을 설정했다. **큰 실수였다**.

```kotlin
// 상황별로 다르게 설정하기 시작했다
// 1. 빠른 캐시 갱신: 사용자가 기다리면 안 됨
@DistributedLock(waitTime = 3L, waitTimeUnit = TimeUnit.SECONDS)
fun refreshProductCache(productId: String)

// 2. 예약 처리: 조금 기다려도 됨, 정확성이 중요
@DistributedLock(waitTime = 30L, waitTimeUnit = TimeUnit.SECONDS) 
fun createReservation(command: CreateReservationCommand)

// 3. 정산 처리: 시간이 걸려도 데이터 정합성 확보가 우선
@DistributedLock(waitTime = 2L, waitTimeUnit = TimeUnit.MINUTES)
fun processPayment(orderId: String)
```

**경험상** 타임아웃은:
- **사용자 대면 기능**: 짧게 (3-10초)
- **백그라운드 작업**: 길게 (1-2분)
- **중요 비즈니스 로직**: 적당히 (30초-1분)

### 모니터링 지표들

실제 운영하면서 **꼭 봐야 할 지표들**을 정리했다:

1. **락 획득 성공률**: 95% 이상 유지 목표
2. **평균 락 대기 시간**: 비즈니스별 기준값 설정
3. **락 보유 시간**: 비즈니스 로직 성능 간접 측정
4. **락 경합 빈도**: 같은 키에 동시 요청하는 스레드 수

```kotlin
// 이런 로그들을 남기기 시작했다
logger.info("Lock acquired: key={}, waitTime={}ms, threadId={}", lockKey, waitTime, threadId)
logger.warn("Lock acquisition failed: key={}, waitTime={}ms, threadId={}", lockKey, waitTime, threadId)
```

### Fair Lock의 성능 특성

실제 부하 테스트 결과:
- **일반 락**: 처리량 높음, 순서 보장 없음
- **Fair 락**: 처리량 20-30% 감소, 순서 보장 완벽

**내가 내린 결론**: 
- **예약 시스템처럼 공정성이 중요한 곳**: Fair Lock 쓰자
- **캐시 갱신 같이 순서가 상관없는 곳**: 일반 Lock 쓰자

성능보다는 **비즈니스 요구사항**이 우선이다.

### 장애 상황 대응

Redis 장애 시나리오별 대응책을 만들었다:

```kotlin
// 1. Redis 연결 실패 시: Fallback 로직
try {
    acquireDistributedLock()
    businessLogic()
} catch (RedisConnectionException e) {
    // 로컬 락으로 임시 대응 (완벽하지 않지만 시스템 다운보다는 나음)
    executeWithLocalLock()
}

// 2. 락 획득 타임아웃 시: 사용자 친화적 메시지  
catch (LockTimeoutException e) {
    throw TooManyRequestException("지금 예약이 몰리고 있어요. 잠시 후 다시 시도해주세요.")
}
```

**완벽한 솔루션은 없다**. 상황에 맞는 **합리적인 타협점**을 찾는 게 중요하다.

---

## 결론

### 핵심 장점

1. **선언적 사용**: 어노테이션으로 간단한 적용
2. **유연한 키 관리**: SpEL을 통한 동적 키 생성
3. **공정성 제어**: Fair Lock으로 순서 보장
4. **모듈화**: Template 패턴으로 락 타입별 구현 분리
5. **안전성**: ThreadLocal 기반 락 인스턴스 관리

### 적용 권장사항

- **공정성이 중요한 예약 시스템**: Fair Lock 사용
- **고성능이 필요한 캐시 갱신**: 일반 Lock 사용  
- **세분화된 락 설계**: 불필요한 락 경합 최소화
- **적절한 타임아웃**: 비즈니스 특성에 맞는 대기 시간 설정
- **모니터링**: 락 성능 지표 지속적 관찰

이러한 분산 락 구현을 통해 **Resource Racing** 같은 동시성 문제를 효과적으로 해결하고, 안정적인 예약 시스템을 구축할 수 있다.