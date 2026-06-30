# schedule 컨텍스트

> 쓰기 모델: **상태 + Outbox** · 매장 운영 설정 (시간·휴일·테이블) · timetable 슬롯 생성의 원천

---

## 1. V1 현행 분석

### 애그리거트: Schedule

```
Schedule
├── restaurantId: String (= PK, restaurant와 1:1)
├── status: ScheduleActiveStatus (ACTIVE | INACTIVE)
├── timeSpans: List<TimeSpan>
│   └── TimeSpan(id, restaurantId, day, startTime, endTime)
├── holidays: List<Holiday>
│   └── Holiday(id, restaurantId, date)
└── tables: List<Table>
    └── Table(id, restaurantId, tableNumber, tableSize)
```

- **행위**: `addHoliday()` (중복 방지), `addTimeSpan()` (중복 방지), `snapshot()`
- **도메인 서비스**: `CreateScheduleDomainService`, `CreateTimeSpanDomainService`, `CreateHolidayDomainService` — 각각 검증+생성
- **이벤트**: 없음. restaurant의 `CreateScheduleEvent`를 구독하여 Schedule 생성
- **정책**: restaurantId 포맷 검증, 시간 start < end, 휴일 미래 날짜

### V1 한계

| 한계 | 설명 |
|------|------|
| 슬롯 생성 로직 불명확 | "해당 달 마지막에 기본값으로 생성" 요구사항이 있으나, timeSpan→timetable 슬롯 변환 로직이 분리되지 않음 |
| 테이블 관리 빈약 | `Table` 엔티티가 있으나 CRUD 외 행위 없음 |
| 이벤트 없음 | 운영시간·휴일 변경 시 timetable 슬롯에 반영하는 이벤트 경로 없음 |

---

## 2. V2 이벤트 스토밍

### 역할

schedule은 매장의 **운영 설정**(언제 열고, 언제 쉬고, 테이블이 몇 개인가)을 관리한다. timetable의 구체 슬롯 생성은 schedule의 설정을 원천으로 삼되, **슬롯 생성 자체는 timetable 쪽 책임**이다.

### 액터 → 커맨드 → 이벤트

| 액터 | 커맨드 | 애그리거트 | 도메인 이벤트 | 정책 / 후속 |
|------|--------|-----------|-------------|-------------|
| — (이벤트) | `InitSchedule` ← `RestaurantRegistered` | Schedule | `ScheduleInitialized` | restaurant 생성 시 자동 |
| 매장 점주 | `SetTimeSpans` | Schedule | `TimeSpansUpdated` | → timetable 슬롯 재생성 트리거 |
| 매장 점주 | `SetHolidays` | Schedule | `HolidaysUpdated` | → timetable 해당 날짜 슬롯 차단 |
| 매장 점주 | `SetTables` | Schedule | `TablesUpdated` | → timetable 슬롯 재생성 트리거 |
| 매장 점주 | `ActivateSchedule` | Schedule | `ScheduleActivated` | 설정 완료 후 활성화 |
| 시스템 (월말) | `GenerateMonthlySlots` | — | — | schedule 설정 기반 timetable 슬롯 일괄 생성 |

### 불변식

| # | 불변식 |
|---|--------|
| 1 | 같은 요일·시간대의 중복 TimeSpan 금지 |
| 2 | 같은 날짜의 중복 Holiday 금지 |
| 3 | TimeSpan의 startTime < endTime |
| 4 | Holiday는 과거 날짜 불가 |
| 5 | 테이블 번호 유일성 (같은 restaurant 내) |

---

## 3. V1→V2 변경 요약

| 항목 | V1 | V2 |
|------|----|----|
| 이벤트 | 없음 | 5개 (설정 변경 → timetable 연동) |
| 슬롯 생성 | 로직 불분명 | schedule 설정 기반 월말 일괄 생성 (시스템 커맨드) |
| 도메인 서비스 | 3개 (검증+생성) | 검증 → 애그리거트 handle |
