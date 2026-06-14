# RFC-003 — 읽기 모델·일관성

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[03-read-model]] 보강 + [[04.read-model-projection-and-replica]] 개정/비준 (필요 시 신규 ADR)

## 배경

query는 query DB의 projection만 읽는다(replica=HA, 라우팅 없음). 라운드1이 *전략*은 잠갔지만 **"무엇을 어떻게 읽기 모델에 둘지"**의 구체는 거의 안 정했다 — 저빈도 lookup 실현, read-your-writes 예외, layered 세부 규약. 여기서 닫는다.

## 논의 항목

### Q1. 저빈도 lookup(category·company·menu) 실현 방식
- **출처**: [[03-read-model]] · [[04.read-model-projection-and-replica]] · [[13.db-hosting-and-read-write-topology]]
- **옵션**: (a) 경량 projection 테이블 / (b) lookup 컨텍스트가 published한 테이블 구독 / (c) 배포 시 적재되는 참조 데이터(seed)
- **쟁점**: 변화 빈도·소유 컨텍스트별로 다를 수 있음. 컨텍스트별로 (a)/(b)/(c) 귀속을 정한다.

### Q2. 읽기 신선도 예외 (read-your-writes)
- **출처**: [[03-read-model]] · [[09.event-ordering-and-delivery-guarantee]] · [[13.db-hosting-and-read-write-topology]]
- **옵션**: (a) 기본 최종 일관성 유지 / (b) 동기 프로젝션(특정 화면) / (c) 버전 토큰(read-your-writes) / (d) 특정 read만 command DB 직접 읽기(정적 바인딩 예외)
- **쟁점**: "쓰고 바로 읽으면 없다"가 기본 사양. *어떤 화면이 즉시 반영을 요구하는지* 증명되면 그 화면만 예외. 지금은 **정책(기본=최종일관성, 예외 승인 기준)**을 결정.

### Q3. 프로젝션 지연 허용치
- **출처**: [[03-read-model]] · [[04.read-model-projection-and-replica]]
- **측정 트리거**: 허용치 *정책*(예: p99 지연 목표 + 초과 시 알람)은 지금, 절대 숫자는 [[RFC-004-messaging-delivery]] lag 측정과 함께 튜닝.

### Q4. 컨텍스트별 초기 읽기 전략(현재 "초안") 확정
- **출처**: [[03-read-model]] §컨텍스트별 초기 읽기 전략(초안)
- **쟁점**: 표가 초안. schedule = 프로젝션 vs 경량 lookup("변화 빈도 보고 결정")을 포함해 컨텍스트별 확정.

### Q5. 프로젝션 선제 적용 대상·시점 범위
- **출처**: [[03-read-model]] (YAGNI 주석)
- **쟁점**: "실제 읽기 요구가 있는 곳부터"가 원칙. 1차 전환에서 실제로 projection을 만들 컨텍스트 목록·기준을 확정.

### Q6. 비-ES 컨텍스트의 읽기: projection vs 기존 QueryDSL 유지
- **출처**: [[03-open-decisions]] Decision C-4
- **쟁점**: 비-ES 컨텍스트도 query DB projection으로 통일할지, 기존 QueryDSL 조회를 남길지.

### Q7. query 측 layered 세부 규약
- **출처**: [[03.command-hexagonal-query-layered]]
- **쟁점**: web/service/repository/projection/model 레이어의 **트랜잭션 경계**, projection과 service의 책임 분리 규약.

## 닫는 방식

- Q1·Q2·Q4·Q5·Q6·Q7 = **논의로 지금 결정**.
- Q3 = **측정 트리거**(정책 지금, 숫자 운영시).
- 🌱 없음 — 카탈로그에 의존하는 항목 없음(읽기 전략은 컨텍스트 성격만으로 결정 가능).

## 산출물

- [[03-read-model]] §초안 표 → 확정 표, §일관성 예외 정책 추가.
- [[04.read-model-projection-and-replica]] 미결정 섹션 해소 → `Accepted` 승급.
- 필요 시 신규 ADR(예: "읽기 신선도 예외 정책").

## 관련 문서
- [[RFC-002-decision-queue]] · [[03-read-model]] · [[04.read-model-projection-and-replica]] · [[13.db-hosting-and-read-write-topology]]
