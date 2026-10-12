# Binary PK 연관관계 사이트별 판정 (Association Site Verdicts)

> 작성: 2026-10-06 · 베이스 커밋: `244a93b3` · 브랜치: `refactor/to-binary-pk`
> 입력: [[binary-pk-identifier-inventory]] §3 (13개 사이트) ·
> [[ADR-028-binary-pk-identifier-storage]] 결정 3·잔존 위험 2·3 ·
> 검증: `adapter-module/src/test/kotlin/com/reservation/persistence/binarypk/BinaryPkHibernateVerificationTest.kt`
>
> 목적: inventory §3이 나열한 13개 연관관계 사이트(`@JoinColumn` 7개 + `@OneToMany`
> 역방향 6개) 각각에 대해 "코드를 안 고쳐도 되는지" 또는 "columnDefinition을
> 명시해야 하는지"를 사이트별로 기록한다. U4 1차 시도는 이 판정을 핸드오프
> 산문에만 남겼다는 지적을 받았다 — 이 문서가 그 교정이다.

## 결론 요약

Hibernate가 `@JoinColumn`에 **JDBC 타입**(BINARY)은 항상 참조되는 `@Id`로부터
물려받지만, **길이**(16 vs 기본값 255)는 그 FK 컬럼이 동시에 `@MapsId`로
`@EmbeddedId` 구성요소와 공유되는 경우에만 물려받지 못한다 — 순수 `@ManyToOne
@JoinColumn`(같은 엔티티에 `@EmbeddedId`가 없는 경우)은 길이까지 정확히
물려받는다. 두 경로 모두 `BinaryPkHibernateVerificationTest`로 직접 확인했다
(아래 표의 "실측" 행 참고). 그래서 7개 중 **1개만** `columnDefinition` 명시
재선언이 필요하고, 나머지 6개는 변경이 필요 없다.

## 1. `@JoinColumn` 사이트 (7개 전부, inventory 3a)

| # | 소유 엔티티.컬럼 | `@MapsId` 동반 | 판정 | 근거 |
|---|---|---|---|---|
| 1 | `restaurant_working_day.restaurant_id` (`RestaurantWorkingDayEntity.kt`) | **예** | **명시 선언 필요 — 적용함.** `columnDefinition = "BINARY(16)"`를 `@JoinColumn`에 직접 추가했다. | **실측**: 추가 전에는 Hibernate가 `BINARY(255)`를 생성했다(`SHOW CREATE TABLE`로 확인) — 바이트 변환 자체는 맞았지만(처음 16바이트가 `UuidBinaryCodec.encode` 출력과 일치) 길이가 255라 `ddl-auto: validate`가 실제 `BINARY(16)` 스키마와 불일치로 실패했을 것이다. `columnDefinition` 추가 후 `BinaryPkHibernateVerificationTest.mapsId-shared fk column stores converter bytes too`가 통과한다. |
| 2 | `restaurant_cuisines.restaurant_id` | 아니오 | **상속, 변경 없음.** | **실측**: `columnDefinition` 재선언 없이도 Hibernate가 참조 PK(`RestaurantEntity.identifier`)의 타입(BINARY)과 길이(16)를 그대로 물려받는다 — `BinaryPkHibernateVerificationTest.pure implicit joinColumn inherits full binary16 definition, length included`로 확인. |
| 3 | `restaurant_photo.restaurant_id` | 아니오 | **상속, 변경 없음.** | #2와 동일한 매핑 모양(`@ManyToOne @JoinColumn`, `@MapsId` 없음, `@EmbeddedId` 없음) — 개별 재실측은 하지 않았고 #2의 실측 결과를 구조적 동치로 적용한다. 이 외삽이 틀렸다면 드러나는 지점은 `ddl-auto: validate`(U4 빌드에서 이미 통과) 또는 U6의 라이브 마이그레이션 적용이다. |
| 4 | `restaurant_nationalities.restaurant_id` | 아니오 | **상속, 변경 없음.** | 위와 동일 (구조적 동치, #2로 외삽). |
| 5 | `restaurant_tags.restaurant_id` | 아니오 | **상속, 변경 없음.** | 위와 동일 (구조적 동치, #2로 외삽). |
| 6 | `menu_photo.menu_id` | 아니오 | **상속, 변경 없음.** | 위와 동일 — 참조 PK가 `MenuEntity.identifier`로 다르지만 매핑 모양(순수 `@ManyToOne @JoinColumn`)은 같다. |
| 7 | `timetable_occupancy.timetable_id` | 아니오 | **상속, 변경 없음.** | 위와 동일 — 참조 PK가 `TimeTableEntity.identifier`. |

## 2. `@OneToMany` 역방향 사이트 (6개, inventory 3b)

이 6개는 전부 `mappedBy`로 위 표의 `@JoinColumn` 사이트를 가리키는 **역방향**이다
— 자신의 컬럼을 갖지 않으므로 타입/길이 불일치가 날 수 있는 지점 자체가 없다.

| # | 소유 엔티티.필드 | `mappedBy` 대상 | 판정 |
|---|---|---|---|
| 8 | `RestaurantEntity.workingDays` | #1 | **변경 없음 — 컬럼 없음.** |
| 9 | `RestaurantEntity.tags` | #5 | **변경 없음 — 컬럼 없음.** |
| 10 | `RestaurantEntity.nationalities` | #4 | **변경 없음 — 컬럼 없음.** |
| 11 | `RestaurantEntity.cuisines` | #2 | **변경 없음 — 컬럼 없음.** |
| 12 | `RestaurantEntity.photos` | #3 | **변경 없음 — 컬럼 없음.** |
| 13 | `MenuEntity.photos` | #6 | **변경 없음 — 컬럼 없음.** |

## 3. 같은 배포 단위 요구사항 (ADR-028 잔존 위험 3 체크리스트)

ADR-028이 요구하는 "PK 전환과 그 PK를 참조하는 모든 `@JoinColumn`은 반드시 같은
배포에"를 이 13개 사이트 전부에 대해 확인한다 — 이 단위(U4)는 PK(`TimeBasedPrimaryKey.identifier`,
`RestaurantWorkingDayId.identifier`)와 위 7개 `@JoinColumn` 전부를 **같은 커밋**(현재
워킹트리의 미커밋 변경)에서 함께 전환했다. 6개 `@OneToMany` 역방향은 컬럼이 없어
이 체크리스트에서 제외한다(위 §2).

- [x] #1 `restaurant_working_day.restaurant_id` — PK와 같은 변경에 포함, 추가로 `columnDefinition` 명시
- [x] #2 `restaurant_cuisines.restaurant_id` — PK와 같은 변경에 포함(컬럼 자체는 안 고침, 참조 PK가 같이 바뀜)
- [x] #3 `restaurant_photo.restaurant_id` — 동일
- [x] #4 `restaurant_nationalities.restaurant_id` — 동일
- [x] #5 `restaurant_tags.restaurant_id` — 동일
- [x] #6 `menu_photo.menu_id` — 동일
- [x] #7 `timetable_occupancy.timetable_id` — 동일

## 관련

- [[binary-pk-identifier-inventory]] §3 — 원본 전수조사
- [[ADR-028-binary-pk-identifier-storage]] — 변환 메커니즘 결정, 폴백 경로
- `adapter-module/src/test/kotlin/com/reservation/persistence/binarypk/BinaryPkHibernateVerificationTest.kt` — 이 문서의 "실측" 근거
