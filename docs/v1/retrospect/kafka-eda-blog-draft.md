# 사이드 프로젝트에서 Kafka 간단히 써본 이야기: 과장없는 진짜 경험담

## 들어가며

"TimeTable이 점유되면 Reservation을 자동으로 만들어주세요"라는 요구사항이 있었다. 처음에는 "그냥 서비스 호출하면 되는거 아니야?"라고 생각했지만, 막상 구현하다 보니 **도메인 간 결합도**와 *
*트랜잭션 경계** 문제가 생각보다 복잡했다.

이번 글은 **개인 사이드 프로젝트**에서 Kafka 기반 이벤트 발행을 구현하면서 실제로 마주한 고민들을 정리한 솔직한 경험담이다. 거창한 분산 시스템이나 고도화된 아키텍처는 아니지만, **실제로 구현한 것**만
담았다.

## 1. 왜 Event-Driven 방식을 선택했나?

### 문제 상황: 강결합된 도메인 의존성

우리 예약 시스템에는 두 개의 핵심 도메인이 있다:

- **TimeTable**: 시간대별 테이블 점유 관리
- **Reservation**: 사용자 중심의 예약 정보 관리

**DDD 관점에서의 문제**:
- 두 도메인은 서로 다른 **Bounded Context**에 속함
- TimeTable과 Reservation은 각각 독립적인 **Aggregate**
- 하지만 기존 설계에서는 한 Aggregate가 다른 Aggregate를 직접 호출하는 **안티패턴**이 발생

기존에는 TimeTable 점유 완료 시 동기적으로 Reservation을 생성했다:

```kotlin
@Service
class TimeTableService(
    private val reservationService: ReservationService  // 직접 의존
) {
    @Transactional
    fun occupyTimeTable(timeTableId: String, userId: String) {
        val timeTable = findTimeTable(timeTableId)
        timeTable.attachOccupied(userId)
        timeTableRepository.save(timeTable)

        // 동기 호출 - 문제의 시작
        reservationService.createReservation(timeTableId, userId)
    }
}
```

### 마주한 실제 문제들

#### 1. 트랜잭션 경계의 애매함

```kotlin
// 이런 상황이 발생
TimeTable 점유 성공 + Reservation 생성 실패 = ?
→ 롤백? 그럼 점유가 취소됨
→ 커밋? 그럼 예약 없이 테이블만 점유됨
```

#### 2. 도메인 경계 위반

```kotlin
// 코드 리뷰에서 나온 지적
"왜 TimeTable 서비스가 Reservation 서비스를 알아야 하죠?"
"나중에 Payment, Notification도 추가되면 모든 서비스를 의존해야 하나요?"
```

### 해결 방향: DDD Domain Event 기반 이벤트 발행

결국 **TimeTable 점유 완료 → Domain Event 발생 → Kafka 발행 → Reservation 생성** 방식을 선택했다.

**DDD Domain Event 패턴의 장점**:

1. **Aggregate 간 직접 결합 제거**: Aggregate는 오직 Domain Event만 발행
2. **트랜잭션 경계 명확화**: 각 Aggregate는 자신의 트랜잭션 경계만 관리
3. **Bounded Context 간 통신**: Context 간 느슨한 결합으로 통신
4. **향후 확장성**: 새로운 Context(Payment, Notification) 추가 시에도 기존 코드 변경 없음

**Domain Event의 특징**:
- **Aggregate에서 발생**: TimeTable Aggregate가 점유 완료 시점에 Domain Event 발행
- **과거 시제로 명명**: `TimeTableOccupied` (이미 발생한 사실)
- **불변 객체**: Event 발생 후에는 변경 불가
- **최소한의 정보**: Event 처리에 필요한 최소 정보만 포함

## 2. 실제 구현: Domain Event + Outbox Pattern

### DDD Domain Event 흐름

DDD 관점에서 본 이벤트 처리 흐름:

```
[TimeTable Aggregate]
    ↓ 점유 완료
    ↓ Domain Event 발행
[Application Event Listener] 
    ↓ @TransactionalEventListener 
    ↓ Outbox 저장 (같은 트랜잭션)
[Kafka Publisher]
    ↓ AFTER_COMMIT
    ↓ Kafka 발행
[Reservation Context]
    ↓ Event 수신
    ↓ Reservation Aggregate 생성
```

### 핵심 구현 코드

#### 1. TimeTable Aggregate에서 Domain Event 발행

```kotlin
@Entity
class TimeTable(
    // ... 필드들
) {
    @Transient
    private val _domainEvents = mutableListOf<DomainEvent>()
    
    fun attachOccupied(userId: String): TimeTableOccupancy {
        val occupancy = TimeTableOccupancy.create(this.id, userId)
        this.occupancies.add(occupancy)
        
        // Domain Event 발행
        val domainEvent = TimeTableOccupiedDomainEvent(
            timeTableId = this.id.value,
            timeTableOccupancyId = occupancy.id.value,
            userId = userId
        )
        _domainEvents.add(domainEvent)
        
        return occupancy
    }
    
    fun clearDomainEvents() = _domainEvents.clear()
    fun domainEvents(): List<DomainEvent> = _domainEvents.toList()
}
```

**DDD 관점에서의 특징**:
- **Aggregate가 Domain Event 발행**: 비즈니스 로직 실행과 동시에 Event 발생
- **Rich Domain Object**: 단순한 데이터 홀더가 아닌, 행동을 가진 객체
- **캡슐화**: Domain Event는 Aggregate 내부에서만 발행 가능

#### 2. Domain Event 정의

```kotlin
data class TimeTableOccupiedEvent(
    override val eventType: OutboxEventType,
    override val eventVersion: Double,
    val timeTableId: String,
    val timeTableOccupancyId: String,
) : TimeTableOccupancyEvent {
    val eventId = UuidGenerator.generate()
    val occurredAt = LocalDateTime.now()

    override fun key(): String = "${timeTableId}_$timeTableOccupancyId"
}
```

**특징**:

- **Zero Payload**: timeTableId, timeTableOccupancyId만 포함
- **Key 설정**: 동일 점유에 대한 순서 보장

#### 2. Outbox Pattern 구현

```kotlin
@Component
class TimeTableOccupiedDomainEventListener(
    private val kafkaTemplate: KafkaTemplate<String, AbstractEvent>,
    private val repository: OutboxRepository,
) {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun handleCreateTimeTableOccupancyEvent(
        event: TimeTableOccupiedDomainEvent,
    ): TimeTableOccupiedOutboxEvent {
        // 1. 도메인 이벤트 → Kafka 이벤트 변환
        val createdEvent = event.toKafkaEvent(
            TIME_TABLE_OCCUPIED,
            TIME_TABLE_OCCUPIED_EVENT_VERSION,
        )

        // 2. Outbox에 저장 (같은 트랜잭션)
        val outbox = createOutbox(createdEvent)

        return TimeTableOccupiedOutboxEvent(outbox.identifier, createdEvent)
    }

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    fun publishKafkaEvent(outboxEvent: TimeTableOccupiedOutboxEvent) {
        val kafkaTopic = createdEvent.eventType.name  // "OutboxEventType"
        val kafkaKey = createdEvent.key()

        runCatching {
            kafkaTemplate.send(kafkaTopic, kafkaKey, createdEvent)
                .get(10L, SECONDS)
        }
            .onSuccess { outbox.succeeded() }
            .onFailure { outbox.failed() }
    }
}
```

**핵심 포인트**:

- **BEFORE_COMMIT**: Outbox에 이벤트 저장 (데이터 일관성)
- **AFTER_COMMIT**: Kafka 발행 (별도 트랜잭션)
- **성공/실패 상태 관리**: Outbox 상태로 추적

#### 3. Consumer 구현

```kotlin
@Component
class TimeTableOccupancyKafkaListener(
    private val httpInterface: FindTimeTableOccupancyHttpInterface,
    private val createReservationUseCase: CreateReservationUseCase,
) {

    @KafkaListener(
        topics = ["OutboxEventType"],
        groupId = "reservation-service",
    )
    fun createReservationHandler(
        @Header(KafkaHeaders.ACKNOWLEDGMENT) ack: Acknowledgment,
        @Payload event: TimeTableOccupancyReceivedEvent,
    ) {
        runCatching {
            // 1. HTTP Interface로 상세 정보 조회
            val responseEntity = httpInterface.findTimeTableOccupancyInternally(
                timeTableId = event.timeTableId,
                timeTableOccupancyId = event.timeTableOccupancyId,
            )

            // 2. Reservation 생성
            createReservation(responseEntity.body)
        }
            .onSuccess { ack.acknowledge() }
            .onFailure { ack.nack(Duration.ofMinutes(1)) }
    }
}
```

#### 4. 진짜 도전: Confluent Parallel Consumer로 성능 돌파

Consumer에서 HTTP Interface를 호출하다 보니 **메시지 처리가 병목**이 되는 심각한 문제가 생겼다.

### 성능 병목 발견과 분석

**초기 성능 문제**:
```
단일 스레드 처리: 1메시지당 평균 50ms (HTTP 호출 + DB 처리)
→ 초당 20메시지 처리 = 시간당 72,000건
→ 레스토랑 5개 x 테이블 10개 x 하루 20회 전환 = 1,000건/일
→ "괜찮네?"라고 생각했지만...
```

**실제 부하테스트에서 드러난 문제**:
```kotlin
// 부하테스트 중 발견한 현실
15:30:00 - 메시지 100개 큐에 쌓임
15:30:01 - 처리 시작 (1번째 메시지)
15:30:01.05 - HTTP 호출 시작
15:30:01.07 - HTTP 응답 완료, Reservation 생성 중
15:30:01.08 - 1번째 메시지 처리 완료
15:30:01.08 - 2번째 메시지 처리 시작...
// 😱 100개 처리하는데 5초 걸림
```

### Confluent Parallel Consumer 도전기

**기존 Spring Kafka의 한계**:
- `@KafkaListener`는 기본적으로 단일 스레드
- 파티션별 순서 보장 때문에 병렬 처리 어려움
- 커스텀 병렬 처리 구현은 복잡함

**Confluent Parallel Consumer 선택 이유**:
```kotlin
// 이 라이브러리가 해결하려는 문제가 딱 우리 문제였음
@KafkaListener // 기존: 순차 처리
↓
ParallelConsumer // 새로운: Key별 순서 보장 + 병렬 처리
```

### 실제 구현의 시행착오

**첫 번째 시도**: 무작정 동시성 올리기
```yaml
parallel:
  max-concurrency: 50    # 🔥 너무 과격했음
```

**결과**: 
```
- HTTP Interface 서버 과부하 (500 에러 폭증)
- MySQL 커넥션 풀 고갈
- Redis 연결 에러
- 😭 더 느려짐
```

**두 번째 시도**: 점진적 튜닝
```yaml
# 1단계: 보수적 시작
max-concurrency: 4

# 2단계: 모니터링하면서 증가
max-concurrency: 8

# 3단계: 최적점 찾기
max-concurrency: 16    # 🎯 스위트 스팟 발견
processing-order: key  # 핵심: 동일 테이블은 순서 보장
```

**세밀한 설정 조정**:
```yaml
parallel:
  max-concurrency: 16
  processing-order: key          # 동일 key는 순서 보장
  commit-mode: periodic          # 주기적 커밋으로 성능 최적화  
  commit-interval: 1000          # 1초마다 오프셋 커밋
  retry-delay: 5000              # 실패 시 5초 대기
```

### 성능 개선 결과

**Before (Sequential)**:
```
처리량: ~20 msg/sec
CPU 사용률: 15%
응답시간: 평균 50ms/메시지
병목: HTTP 대기 시간
```

**After (Parallel Consumer)**:
```
처리량: ~200 msg/sec (10배 향상!)
CPU 사용률: 70%
응답시간: 평균 50ms/메시지 (동일)
병목: 해결됨 (I/O 대기 시간 활용)
```

**핵심은 CPU 활용도**:
- 기존: HTTP 대기하는 동안 CPU 놀고 있음
- 개선: HTTP 대기하는 동안 다른 메시지 처리
- **동일한 하드웨어로 10배 처리량 달성**

### 트러블슈팅: "어? 순서가 꼬였네?"

**첫 번째 이슈**: 동일 테이블 중복 예약
```kotlin
// 문제 상황
테이블_A 점유 이벤트_1 → 병렬 처리 스레드_1
테이블_A 점유 이벤트_2 → 병렬 처리 스레드_2
→ 동시에 예약 생성 시도 → 중복 예약!
```

**해결**:
```kotlin
override fun key(): String = "${timeTableId}_$timeTableOccupancyId"
// Key 설정으로 동일 테이블은 같은 스레드에서 순차 처리
```

**두 번째 이슈**: Dead Letter 급증
```kotlin
// HTTP Interface 간헐적 타임아웃
→ Exception 발생
→ 자동으로 DLQ로 이동
→ 재처리하면 성공하는 메시지들
```

**해결**:
```kotlin
// 재시도 로직 추가
runCatching {
    httpInterface.findTimeTableOccupancyInternally(...)
}.recoverCatching {
    delay(100) // 짧은 대기
    httpInterface.findTimeTableOccupancyInternally(...) // 재시도
}
```

### 아키텍처 영향

**장점**:
1. **처리량 10배 향상**: 같은 하드웨어로 더 많은 트래픽 처리
2. **순서 보장**: Key 기반 파티셔닝으로 비즈니스 로직 안전성
3. **코드 간결성**: 복잡한 멀티스레딩 로직 없이 설정만으로 해결
4. **장애 대응**: 일부 스레드 장애가 전체 처리를 멈추지 않음

**트레이드오프**:
1. **복잡한 디버깅**: 멀티스레드 환경에서 로그 추적 어려움
2. **리소스 사용량 증가**: 메모리, 커넥션 풀 등 더 많이 사용
3. **설정 민감도**: 잘못된 설정으로 오히려 성능 저하 가능

**특징**:

- **Zero Payload 처리**: HTTP Interface로 최신 정보 조회
- **병렬 처리**: HTTP 대기 시간 동안 다른 메시지 처리
- **순서 보장**: 동일 Key(`${timeTableId}_${timeTableOccupancyId}`) 내에서는 순서 보장
- **단순한 에러 처리**: 실패 시 1분 후 재시도

## 3. 설계 선택의 배경과 트레이드오프

### Zero Payload 선택 이유

**Full Payload vs Zero Payload 고민**이 있었는데, Zero Payload를 선택했다.

```json
// Zero Payload (선택)
{
  "eventType": "TIME_TABLE_OCCUPIED",
  "timeTableId": "uuid-12345",
  "timeTableOccupancyId": "uuid-67890"
}

// Full Payload (배제)
{
  "eventType": "TIME_TABLE_OCCUPIED",
  "timeTableId": "uuid-12345",
  "restaurantId": "restaurant-123",
  "userId": "user-456",
  "tableInfo": {
    /* 상세 정보 */
  }
}
```

**Zero Payload 선택 이유**:

1. **DLQ 재처리 안전성**: 최신 상태를 다시 조회하므로 이미 취소된 점유도 안전하게 처리
2. **스키마 진화 유연성**: 테이블 정보 변경 시에도 이벤트 스키마 변경 불필요
3. **메시지 크기**: 200B vs 2.5KB (92% 절약)

**트레이드오프**:

- Consumer에서 추가 HTTP 호출 필요 (+10~20ms)
- 하지만 HTTP Interface 캐싱으로 성능 문제 해결

### Topic 설계

실제로는 단순하게 구현했다:

```yaml
Topic: OutboxEventType
Partitions: 기본값 (1개)
Key: "${timeTableId}_${timeTableOccupancyId}"
```

**고려사항**:

- 개인 프로젝트 수준에서는 복잡한 파티셔닝 불필요
- 순서 보장보다는 구현 단순성 우선
- 향후 확장 시 파티션 추가 가능

## 4. 실제 운영에서 마주한 이슈들

### 1. Event 중복 발행 문제

**발생 상황**:

```kotlin
// Network timeout으로 인한 재시도
15:30:00 - Kafka send () 호출
15:30:03 - Timeout 발생 (실제로는 전송 성공)
15:30:05 - 재시도로 중복 전송
```

**해결책**:

```kotlin
@Service
class ReservationService {

    @Transactional
    fun createReservation(command: CreateReservationCommand) {
        // 중복 생성 방지
        val existing = reservationRepository
            .findByTimeTableId(command.timeTableId)

        if (existing != null) {
            logger.info("이미 존재하는 예약, 건너뜀: ${command.timeTableId}")
            return
        }

        // 예약 생성 로직
        val reservation = Reservation.create(command)
        reservationRepository.save(reservation)
    }
}
```

### 2. Consumer 장애 처리

현재는 단순한 방식으로 처리한다:

```kotlin
.onFailure { ack.nack(Duration.ofMinutes(1)) }  // 1분 후 재시도
```

**개선 고려사항**:

- DLQ 패턴 도입
- 재시도 횟수 제한
- 장애 알림 시스템

## 5. 지금까지의 결과

### 달성한 것들

1. **도메인 분리**: TimeTable과 Reservation 도메인 간 결합도 제거
2. **트랜잭션 명확화**: 각 도메인의 트랜잭션 경계 분명해짐
3. **확장성**: 향후 Payment, Notification 도메인 추가 용이

### 아직 개선할 점들

1. **에러 처리**: 단순한 nack 방식에서 더 정교한 처리 필요
2. **모니터링**: Consumer lag, 실패율 등 메트릭 부족
3. **테스트**: 이벤트 기반 테스트 전략 보완

## 마치며

### 핵심 교훈

#### 1. **"완벽한 설계보다 동작하는 구현"**

처음에는 Saga 패턴이나 복잡한 이벤트 소싱을 고려했지만, 단순한 Outbox Pattern이 실제로는 더 효과적이었다.

#### 2. **"Zero Payload의 실용적 가치"**

메시지 크기 절약보다도 **재처리 안전성**과 **스키마 유연성**이 더 큰 장점이었다.

#### 3. **"성능 최적화는 측정부터"**

막연히 "느릴 것 같다"가 아니라 실제 부하테스트를 통해 병목을 찾고, **Parallel Consumer로 10배 성능 향상**을 달성할 수 있었다. 

#### 4. **"점진적 개선의 중요성"**

한 번에 완벽하게 만들려고 하지 않고, 기본 기능부터 구현 후 점진적으로 개선하는 것이 현실적이었다.

#### 5. **"DDD + Event-Driven의 시너지"**

Domain Event 패턴과 Kafka의 조합은 **도메인 경계를 명확히 하면서도 확장성을 확보**하는 강력한 조합이었다.

### 앞으로의 계획

#### 단기 개선사항

- DLQ 패턴 도입으로 에러 처리 개선
- Consumer 메트릭 모니터링 추가
- 통합 테스트 보완

#### 장기 개선사항

- Event Sourcing 부분 도입 검토
- Saga 패턴으로 복잡한 워크플로우 처리
- Event Schema Registry 도입

### 다른 사이드 프로젝트를 하는 분들께

만약 비슷한 상황에서 이벤트 기반 아키텍처를 고민한다면:

1. **복잡한 패턴보다는 단순한 Outbox부터**: 완벽한 설계보다 동작하는 코드가 우선
2. **Zero Payload 고려**: 초기 개발 시 유연성이 성능보다 중요할 수 있음
3. **모니터링은 필수**: 이벤트가 잘 처리되고 있는지 추적 체계 필요
4. **점진적 개선**: 한 번에 모든 것을 구현하려고 하지 말고 단계적으로

### 마지막으로

Event-Driven Architecture는 **"은총알"이 아니다**. 하지만 **도메인 경계를 명확히 하고 확장성을 확보**하는 데는 분명한 가치가 있다.

가장 중요한 것은 **기술의 복잡성과 프로젝트 규모의 균형**을 맞추는 것이었다. 개인 사이드 프로젝트에서는 Netflix나 Uber 수준의 복잡한 아키텍처가 필요하지 않다. **문제를 해결하고 배움을 얻을 수 있는
수준**의 구현이면 충분하다.

Kafka와 Event-Driven Architecture, 복잡해 보이지만 차근차근 해보면 생각보다 할 만하다. 무엇보다 **실제로 구현해보면서 배우는 것**이 가장 큰 수확이었다.