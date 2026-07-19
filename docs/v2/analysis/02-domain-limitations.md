# V2 Analysis — 02. Domain Limitations (이벤트 소싱 관점)

> "도메인이 빈약할 것"이라는 가설은 **참**으로 확인됐다. 아래는 이벤트 소싱/CQRS 전환을 **얼마나 막는지** 순으로 정리한 한계다.

## 한계 랭킹 (차단도 순)

| # | 한계 | 코드 근거 | 극복 비용 |
|---|------|-----------|-----------|
| 1 | **빈약한 애그리거트** | `restaurant/Restaurant.kt`, `reservation/Reservation.kt`, `user/self/User.kt` | High |
| 2 | **도메인 이벤트 부재** | `timetable`·`restaurant` 2개 컨텍스트만 (각 1건: `TimeTableOccupiedDomainEvent`, `CreateScheduleEvent`) — 9개 중 2개 | Very High |
| 3 | **이벤트 스토어 개념 없음** | 약 12종 `*Snapshot` 은 단방향 DTO | Very High |
| 4 | **읽기/쓰기 동일 모델·DB** | `RestaurantEntity` 가 command·query 공용 | Medium |
| 5 | **가변 상태 (불변 아님)** | 애그리거트 전반 `var` + setter | High |
| 6 | **동시성 제어 미흡** | `@Version` 15개 중 1개(`TimeTableEntity`) | Medium |

---

## 1. 빈약한 애그리거트 — 상태 변경이 애그리거트 *밖*에서 일어난다 (1순위 장애물)

- 비즈니스 로직의 ~95%가 **16개의 `*DomainService`** 에 있다. 애그리거트는 `var` + setter 데이터 홀더.
- 검증·정책·불변식이 애그리거트가 아닌 서비스에서 강제된다. 예 — `CreateRestaurantDomainService`:

```kotlin
fun create(form: CreateRestaurantForm): RestaurantSnapshot {
    validate(form)                          // 불변식이 서비스에 있음 (애그리거트 아님)
    val restaurant = Restaurant(...)
    updateWorkingDays(restaurant, ...)      // 서비스가 애그리거트를 외부에서 변이
    updatePhotos(restaurant, ...)
    return restaurant.snapshot()            // 도메인 객체가 아니라 DTO 반환
}
```

**왜 1순위인가**: 이벤트 소싱에서 애그리거트는 `handle(command) → List<Event>` + `apply(event) → newState` 를 *스스로* 해야 한다. 지금처럼 로직이 서비스에 흩어져 있으면 "어떤 이벤트가 언제 발생하는가"의 권위가 애그리거트에 없어, 이벤트 모델을 신뢰성 있게 도출할 수 없다. → **모든 커맨드 처리 재설계 필요.**

> 이 취약점은 **W-1**로 분류한다. V2 대응(애그리거트 설계 규칙·로직 배치 triage)은 [[05-aggregate-design]] 참조.

## 2. 도메인 이벤트 부재

- 도메인 이벤트는 `timetable`(`TimeTableOccupiedDomainEvent`)·`restaurant`(`CreateScheduleEvent`) 2개 컨텍스트에만, 각 1건씩 있다. 나머지 7개 컨텍스트의 상태 변화(예약 생성/취소, 회원 탈퇴 등)는 **이벤트로 표현되지 않는다.**
- 둘 다 `core-module` 의 `*/event/` 에 있으나, 발행·소비는 어댑터/애플리케이션 계층(`@TransactionalEventListener` 등)에서 일어난다 — **애그리거트가 직접 내는 구조가 아니다.**
- → 상태변화의 ~85%+가 이벤트 미적용. 이벤트 모델링을 **거의 처음부터** 해야 한다.

## 3. 이벤트 스토어 개념 없음 — 스냅샷은 단방향 DTO

- `snapshot()`/`*Snapshot` 은 **애그리거트 → 영속성** 변환용 DTO다. "이벤트로부터 상태 재구성"이나 "애그리거트별 이벤트 스트림"이 아니다.
- 진실의 원천은 **JPA 테이블의 현재 상태**다. 이벤트 리플레이·시점 조회(temporal query)·감사 로그가 불가능.
- → 이벤트 스토어(append-only) 설계와 리빌드 로직이 신규로 필요. (단, 기존 스냅샷 패턴은 ES의 *스냅샷 최적화*로 재활용 가능.)

## 4. 읽기/쓰기 동일 모델·DB

- 포트는 명령/조회로 나뉘지만 저장소는 같은 엔티티·테이블·DB. 읽기 전용 프로젝션 없음.
- → CQRS의 핵심인 "읽기 모델을 쓰기 모델과 독립적으로 최적화"가 안 됨. (비용은 중간 — 어댑터 계층에서 read projection 추가 가능.)

## 5. 가변 상태

- `var` 필드 + 외부 manipulator. 불변 복사(`copy()`) 아님.
- → 이벤트 소싱의 `apply(event)`는 보통 불변 전이를 선호. 동시성·재구성 안전성을 위해 불변 애그리거트로 전환 권장.

## 6. 동시성 제어 미흡

- `@Version` 이 `TimeTableEntity` 1개뿐. 애그리거트 버전(스트림 시퀀스) 개념 없음.
- → 이벤트 소싱은 "기대 버전 N에 append" 같은 **낙관적 동시성**이 필수. 애그리거트별 버전/시퀀스 도입 필요.

---

## 토대로 재활용 가능한 것 (한계와 함께 기억할 것)

- ✅ 헥사고날 포트/어댑터 + 도메인/JPA 분리 → 어댑터 교체 용이
- ✅ **검증된 Transactional Outbox (timetable)** → 이벤트 드리븐 발행 인프라 재사용
- ✅ `AbstractEvent`(eventType/eventVersion/`@class` 다형성) → 이벤트 직렬화/버저닝 토대
- ✅ 스냅샷 패턴 → ES 스냅샷 최적화로 전용 가능
- ✅ 시간 기반 UUID → 분산 식별자 준비됨

> 결론: 가장 비싼 작업은 **#1 애그리거트 행위화**와 **#2/#3 이벤트 모델·스토어 신설**이다. 이 둘의 범위를 어디까지 가져갈지가 `03-open-decisions.md`의 핵심 결정이다.
