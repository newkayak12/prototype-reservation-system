# Restaurant Schedule Management System Design

## 개요
예약 시스템에서 Restaurant 생성 후 Schedule 관련 시스템 초기화에 대한 설계 과정과 최종 결정사항을 정리한 문서입니다.

## 배경
Restaurant 엔티티 생성 후 예약 시스템을 활성화하기 위해서는 다음 정보들이 필요합니다:
- **Table 정보**: 테이블 수, 좌석 수, 테이블 타입
- **TimeSpan 정보**: 운영 시간대 (예: 09:00-22:00)
- **Holiday 정보**: 휴무일 설정
- **TimeTable 생성**: 배치 작업을 통한 예약 가능 시간대 생성

이러한 정보들이 모두 설정되어야만 실제 예약을 받을 수 있는 상태가 됩니다.

## 설계 시행착오 과정

### 1차 시도: 도메인 이벤트 기반 자동 초기화
**접근 방식**: Restaurant 생성 후 CreateScheduleEvent를 발행하여 Schedule 관련 데이터를 자동 생성

```kotlin
// 구현했던 코드
@Transactional
override fun execute(request: CreateProductCommand): Boolean {
    val restaurantId = insertRestaurant(snapshot)
    applicationEventPublisher.publishEvent(CreateScheduleEvent(restaurantId))
    return restaurantId != null
}
```

**문제점**:
- Restaurant 생성 직후 자동으로 생성할 Schedule 데이터가 실제로는 없음
- Table, TimeSpan, Holiday는 모두 사업자가 수동으로 설정해야 하는 정보
- 의미 없는 빈 데이터만 생성하게 됨

**결과**: 도메인 이벤트 자체가 불필요한 것으로 판명

### 2차 시도: 단순 집계 테이블
**접근 방식**: Schedule을 TimeSpan, Holiday, Table의 개수를 저장하는 집계 테이블로 활용

```sql
-- 시도했던 구조
CREATE TABLE schedule (
    restaurant_id VARCHAR(255) PRIMARY KEY,
    total_time_spans INT DEFAULT 0,
    total_holidays INT DEFAULT 0, 
    total_tables INT DEFAULT 0,
    total_capacity INT DEFAULT 0
);
```

**문제점**:
- 단순 COUNT 쿼리로 충분히 해결 가능한 수준
- 데이터 동기화 복잡성 증가 (Holiday 추가/삭제 시마다 Schedule 업데이트 필요)
- 실제 성능상 이점도 없음
- 불필요한 복잡성만 추가

**결과**: 과도한 최적화로 판명, 실용성 부족

### 3차 시도: 예약 시스템 준비도 관리 테이블 (최종 선택)
**접근 방식**: Schedule을 예약 시스템의 설정 완료 상태와 운영 현황을 관리하는 테이블로 활용

## 최종 설계

### Restaurant Schedule 테이블 구조
```sql
CREATE TABLE restaurant_schedule (
    restaurant_id VARCHAR(255) PRIMARY KEY,
    
    -- 설정 완료 상태
    tables_configured BOOLEAN DEFAULT FALSE,        -- 테이블 설정 완료 여부
    working_hours_configured BOOLEAN DEFAULT FALSE, -- 운영시간 설정 완료 여부  
    holidays_configured BOOLEAN DEFAULT FALSE,      -- 휴무일 설정 완료 여부
    
    -- 운영 현황
    is_reservation_active BOOLEAN DEFAULT FALSE,    -- 예약 접수 활성화 여부
    last_batch_run_at TIMESTAMP NULL,              -- 마지막 배치 실행 시간
    next_batch_scheduled_at TIMESTAMP NULL,        -- 다음 배치 예정 시간
    
    -- 집계 정보 (캐시용)
    total_tables INT DEFAULT 0,
    total_capacity INT DEFAULT 0,
    total_working_hours_per_day DECIMAL(4,2) DEFAULT 0,
    
    -- 상태 정보
    setup_completion_rate DECIMAL(3,2) DEFAULT 0,  -- 설정 완료율 (0.00 ~ 1.00)
    status ENUM('SETUP', 'READY', 'ACTIVE', 'INACTIVE') DEFAULT 'SETUP',
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### CreateScheduleService 역할
```kotlin
@UseCase  
class CreateScheduleService {
    fun execute(restaurantId: String) {
        val schedule = RestaurantSchedule(
            restaurantId = restaurantId,
            status = ScheduleStatus.SETUP,
            setupCompletionRate = 0.0,
            tablesConfigured = false,
            workingHoursConfigured = false,
            holidaysConfigured = false,
            isReservationActive = false
        )
        
        saveSchedule(schedule)
    }
}
```

### 상태 관리 플로우
```
SETUP → READY → ACTIVE
  ↓       ↓       ↓
설정중   설정완료  예약활성화
```

1. **SETUP**: 초기 상태, 사업자가 Table/TimeSpan/Holiday 설정 중
2. **READY**: 모든 설정 완료, 배치 실행 가능 상태
3. **ACTIVE**: 배치 실행 완료, 예약 접수 가능 상태
4. **INACTIVE**: 일시적 비활성화 (점검, 휴업 등)

## 비즈니스 가치

### 1. 사업자 대시보드
- 설정 완료 진행률 표시: "설정 완료율 60% - 테이블 정보를 입력하세요"
- 예약 시스템 활성화 상태 한눈에 파악
- 다음 할 일 가이드 제공

### 2. 시스템 운영
- 예약 접수 가능 여부 빠른 판단
- 배치 실행 조건 검증
- 설정 변경 시 자동 상태 업데이트

### 3. 고객 경험
- 예약 불가능한 음식점에 대한 명확한 상태 표시
- "준비 중" vs "일시 중단" 구분 가능

## 구현 고려사항

### 상태 업데이트 시점
```kotlin
// Table 설정 완료 시
fun onTableConfigured(restaurantId: String) {
    updateScheduleStatus(restaurantId) {
        tablesConfigured = true
        recalculateSetupCompletionRate()
        updateStatusIfReady()
    }
}

// 모든 설정 완료 검증
fun updateStatusIfReady(schedule: RestaurantSchedule) {
    if (schedule.tablesConfigured && 
        schedule.workingHoursConfigured && 
        schedule.holidaysConfigured) {
        schedule.status = ScheduleStatus.READY
        schedule.setupCompletionRate = 1.0
    }
}
```

### 배치 연동
```kotlin
// 배치 실행 가능 여부 체크
fun canRunBatch(restaurantId: String): Boolean {
    val schedule = getSchedule(restaurantId)
    return schedule.status == ScheduleStatus.READY
}

// 배치 실행 후 상태 업데이트
fun onBatchCompleted(restaurantId: String) {
    updateScheduleStatus(restaurantId) {
        status = ScheduleStatus.ACTIVE
        isReservationActive = true
        lastBatchRunAt = LocalDateTime.now()
    }
}
```

## 교훈 및 원칙

### 1. YAGNI (You Aren't Gonna Need It) 원칙
- "뭔가 필요할 것 같다"는 막연한 추측으로 설계하지 말 것
- 실제 비즈니스 요구사항이 명확할 때만 구현

### 2. 실제 비즈니스 가치 고려
- 기술적 흥미가 아닌 비즈니스 가치 중심으로 설계
- 사용자(사업자, 고객)에게 실질적 도움이 되는지 검증

### 3. 단순함의 힘
- 복잡한 설계보다 단순하고 명확한 설계가 유지보수에 유리
- 과도한 최적화는 오히려 시스템을 복잡하게 만듦

### 4. 점진적 설계
- 처음부터 완벽한 설계를 추구하지 말고 필요에 따라 점진적으로 발전
- 시행착오를 통해 더 나은 설계에 도달

## 향후 확장 계획
1. 예약률 기반 자동 상태 조정
2. 설정 변경 히스토리 추적
3. 다국어 상태 메시지 지원
4. 알림 시스템 연동

## 참고자료
- ADR-002: Domain Events for Schedule Creation (초기 도메인 이벤트 설계)
- Restaurant 예약 시스템 요구사항 문서
- DDD 패턴 가이드라인

---
작성일: 2025-01-01  
작성자: 개발팀  
검토자: 아키텍처 팀