# RFC-005 — 이벤트 스토어·스키마 진화

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[08-event-store-lifecycle]] · [[05.event-store-mysql-table]] · [[10.event-schema-evolution]] 보강·비준 (Proposed→Accepted)

## 배경

MySQL append-only 이벤트 스토어 + N마다 스냅샷 + 읽기 시 업캐스팅 + JSON 페이로드 + 논리 타입명(eventType) 디스크리미네이터 — 이 **메커니즘**은 라운드1에서 잠갔다. 미결은 그 위의 구체 수치·정책·구현 방식이다. 여기서 닫는다.

## 논의 항목

### Q1. 스냅샷 주기 N (초안 50~100)
- **출처**: [[05.event-store-mysql-table]] · [[02-write-model]] · [[08-event-store-lifecycle]] · [[11-environments-and-testing]]
- **측정 트리거**: 정책(스냅샷 주기를 둔다·트리거는 이벤트 카운트)은 지금, 절대 N은 §5.4 k6 스윕(재구성 지연 vs 스냅샷 쓰기 비용) 결과로 튜닝.

### Q2. 스냅샷 보관 개수
- **출처**: [[08-event-store-lifecycle]]
- **옵션**: (a) 최신 1개만 / (b) 디버깅·롤백용 최근 N개 보존
- **쟁점**: 보관량 vs 과거 시점 재구성·디버깅 편의. 보존한다면 GC 기준.

### Q3. 스냅샷-이벤트 정합성 검증(reconciliation)
- **출처**: [[08-event-store-lifecycle]]
- **측정 트리거**: "스냅샷 = 0..version 이벤트 재생과 일치"를 검증하는 빈도·표본률. 정책(검증을 돌린다)은 지금, 빈도·표본률은 운영 부하 보고 튜닝.

### Q4. 보존·파티셔닝 단위
- **출처**: [[05.event-store-mysql-table]] · [[08-event-store-lifecycle]]
- **옵션**: (a) 시간(생성월 등) 파티셔닝 / (b) aggregate_type 파티셔닝
- **쟁점**: 보존·아카이빙은 시간이 자연스럽지만, 핫패스 조회는 aggregate 단위(특정 스트림 재생)다. 시간 파티셔닝과 aggregate 단위 조회의 궁합을 측정으로 확인.

### Q5. 콜드 스토리지 종료(핫/콜드 경계) 판정 기준
- **출처**: [[08-event-store-lifecycle]]
- **쟁점**: "이관해도 되는 종료된 스트림"의 정의는 기술이 아니라 **도메인**이 준다(예약 종결 등). 컨텍스트별 "종료" 정의를 모아 경계 기준을 정한다.

### Q6. 콜드 스토리지 매체
- **출처**: [[08-event-store-lifecycle]] · [[11-environments-and-testing]]
- **옵션**: (a) 같은 DB의 아카이브 테이블 / (b) 오브젝트 스토리지(S3 등)
- **쟁점**: 재구성 빈도·복원 경로·셰딩 가능성 대비 운영 단순성.

### Q7. temporal(시점) 질의 노출 범위
- **출처**: [[08-event-store-lifecycle]]
- **옵션**: (a) 운영 도구·디버깅 한정 / (b) 일반 API로 노출
- **쟁점**: "as-of 재구성"을 제품 기능으로 열면 인덱스·성능·권한 부담. 기본은 운영 한정.

### Q8. 업캐스터 등록·탐색 방식
- **출처**: [[10.event-schema-evolution]] · [[08-event-store-lifecycle]]
- **옵션**: (a) 레지스트리 빈에 명시 등록 / (b) 어노테이션 스캔으로 자동 수집
- **쟁점**: 명시성·테스트 용이성 vs 보일러플레이트. (eventType, fromVersion) 키 충돌 탐지 책임 포함.

### Q9. 논리 타입명(eventType) 레지스트리 구현 방식
- **출처**: [[10.event-schema-evolution]]
- **옵션**: (a) `@JsonTypeName` 스캔 / (b) 명시 등록 빈
- **쟁점**: 클래스명 리팩터링에서 논리명을 분리·고정하는 게 목표. 스캔의 암묵성 vs 명시 등록의 안전성.

### Q10. 스키마 레지스트리(Avro/Protobuf) 도입 기준
- **출처**: [[10.event-schema-evolution]]
- **쟁점**: 현재 JSON+업캐스팅으로 **YAGNI 보류**. 외부/폴리글랏 컨슈머의 직접 구독이 증명될 때 도입 기준을 재검토. 여기서는 *기준*만 적는다.

### Q11. 스냅샷 자체 스키마 진화 정책
- **출처**: [[10.event-schema-evolution]] · [[08-event-store-lifecycle]]
- **쟁점**: 이벤트는 업캐스팅하지만 스냅샷 포맷이 바뀌면? (a) 폐기 후 이벤트 재생으로 재생성 / (b) 스냅샷도 업캐스팅. 재생성이 단순하나 비용·시점 기준 필요.

### Q12. 이벤트 페이로드 직렬화 규약 세부
- **출처**: [[05.event-store-mysql-table]]
- **쟁점**: JSON 방향은 확정. 세부 규약 — null/기본값 처리, enum 표현, 시간·금액 타입, 알 수 없는 필드 무시 정책, 컬럼 타입(JSON vs TEXT).

## 닫는 방식

- Q2·Q4·Q5·Q6·Q7·Q8·Q9·Q10·Q11·Q12 = **논의로 지금 결정**(Q8·Q9·Q12는 구현 규약 확정).
- Q1·Q3 = **측정 트리거**(정책 지금, 수치는 운영/k6 시).
- 🌱 없음 — 도메인 이벤트 카탈로그에 강결합된 항목은 없다(Q5의 "종료" 정의만 컨텍스트 성격에 의존, 카탈로그 선행 불요).

## 산출물

- [[08-event-store-lifecycle]] §스냅샷 정책·보존·콜드 이관·temporal 노출 확정.
- [[05.event-store-mysql-table]] §파티셔닝·직렬화 규약 확정.
- [[10.event-schema-evolution]] §업캐스터·eventType 레지스트리 구현 + 스냅샷 진화 정책 → `Accepted` 승급.
- 필요 시 신규 ADR(예: "스키마 레지스트리 도입 기준").

## 관련 문서
- [[RFC-002-decision-queue]] · [[08-event-store-lifecycle]] · [[05.event-store-mysql-table]] · [[10.event-schema-evolution]] · [[11-environments-and-testing]]
