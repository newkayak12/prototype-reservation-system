# ADR-002: Domain Events for Schedule Creation

## Status
Accepted

## Context
예약 시스템에서 Restaurant 생성 후 관련된 Schedule, Holiday, TimeSpan 엔티티들을 생성해야 하는 상황에서 다음과 같은 문제들이 발생했습니다:

1. **생명주기 차이**: Restaurant, Holiday, Routine, TimeTable이 각각 다른 시점에 생성될 수 있음
2. **도메인 분리**: Menu와 동일하게 Schedule 관련 엔티티들을 독립적인 aggregate로 분리 필요
3. **ID 생성 타이밍**: TimeBasedPrimaryKey로 인해 JPA save 이후에만 ID 생성됨
4. **트랜잭션 일관성**: Restaurant 생성 실패 시 Schedule 생성도 롤백되어야 함

## Decision
Domain Events를 사용하여 Restaurant 생성 후 Schedule 생성을 비동기적으로 처리하기로 결정했습니다.

### 아키텍처 구조

```
Restaurant Creation Flow:
1. UseCase: Restaurant 생성 + 트랜잭션 처리
2. UseCase: CreateScheduleEvent 발행
3. EventHandler: @TransactionalEventListener로 Schedule 생성 처리
```

### 구현 방식

#### 1. Domain Event 정의
```kotlin
// core-module
class CreateScheduleEvent(
    val restaurantId: String
)
```

#### 2. UseCase에서 이벤트 발행
```kotlin
// application-module
@UseCase
class CreateRestaurantService(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    @Transactional
    override fun execute(request: CreateProductCommand): Boolean {
        // Restaurant 생성
        val restaurantId = insertRestaurant(snapshot)
        
        // Domain Event 발행
        applicationEventPublisher.publishEvent(CreateScheduleEvent(restaurantId))
        
        return restaurantId != null
    }
}
```

#### 3. Event Handler에서 Schedule 생성
```kotlin
// application-module (향후 구현 예정)
@Component
class ScheduleEventHandler {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleCreateScheduleEvent(event: CreateScheduleEvent) {
        // Schedule 초기 설정 생성
        scheduleService.createInitialSchedule(event.restaurantId)
    }
}
```

### 도메인 모델 분리

#### Independent Aggregates 선택
Menu 패턴과 일관성을 위해 Schedule 관련 엔티티들을 독립적인 aggregate로 분리:

- **Schedule**: Aggregate Root
- **Holiday**: Independent Aggregate 
- **TimeSpan**: Value Object within Schedule
- **TimeTable**: Independent Aggregate (batch 생성용)

#### 비즈니스 로직
```
(일상 날짜 - 휴일) × timespan = 예약 가능 일자
```

## Consequences

### Positive
- **느슨한 결합**: Restaurant와 Schedule 도메인이 독립적으로 발전 가능
- **트랜잭션 안전성**: @TransactionalEventListener로 Restaurant 생성 실패 시 Schedule 생성 방지
- **확장성**: 추후 다른 도메인 이벤트 추가 용이
- **일관성**: 기존 Menu 도메인과 동일한 패턴 적용

### Negative  
- **복잡성 증가**: 이벤트 기반 아키텍처로 인한 추적 복잡성
- **비동기 처리**: 즉시 Schedule을 필요로 하는 경우 처리 복잡
- **디버깅 어려움**: 이벤트 체인 추적 필요

### Risks
- **Event Lost**: 이벤트 발행 실패 시 Schedule 미생성 위험
- **Ordering**: 여러 이벤트 발생 시 순서 보장 필요
- **Testing**: Mock 설정 복잡성 증가

## Implementation Notes

### ID 생성 타이밍 해결
- UseCase에서 `insertRestaurant()`가 String ID 반환
- 이벤트 발행 시점에 이미 ID 확정됨
- `@TransactionalEventListener(AFTER_COMMIT)`로 트랜잭션 안전성 보장

### Testing 고려사항
```kotlin
// MockK에서 타입 명시 필요
every { 
    applicationEventPublisher.publishEvent(any<CreateScheduleEvent>()) 
} just Runs
```

### 향후 확장 계획
1. Schedule 생성 UseCase 및 Port 구현
2. Holiday, TimeSpan 관리 기능 추가
3. TimeTable 배치 생성 로직 구현
4. 이벤트 기반 아키텍처 문서화

## References
- DDD: Domain Events Pattern
- Spring: @TransactionalEventListener
- 기존 Menu 도메인 아키텍처 패턴

## Date
2025-01-01

## Participants
- 개발팀
- 아키텍처 리뷰어