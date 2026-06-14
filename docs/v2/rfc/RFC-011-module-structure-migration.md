# RFC-011 — 모듈 구조·마이그레이션 확정

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[01-module-structure]]·[[04-migration]] 보강 + [[06.strangler-migration]]·[[07.command-domain-jpa-separation]] 비준

## 배경

CQRS-top 모듈 분리(command=hexagonal, query=layered)와 Strangler 점진 전환은 라운드1에서 *원칙*을 잠갔다. 남은 건 순수 도메인의 물리 배치, JPA 매핑 규약, 전환 순서(현재 초안), 신규 기능 투입이다. 여기서 닫는다.

## 논의 항목

### Q1. 순수 도메인의 물리 배치
- **출처**: [[07.command-domain-jpa-separation]] · [[01-module-structure]]
- **옵션**: (a) `command-module` 내 `domain` 패키지 / (b) V1처럼 별도 `core-module` 유지
- **쟁점**: 도메인↔JPA 분리 원칙은 확정. 물리 배치만 미정 — (a)는 컨텍스트 응집·서비스 분리 용이, (b)는 V1 연속성·도메인 순수성 강제가 명시적.

### Q2. 도메인↔JPA 매핑 보일러플레이트 감소 규약
- **출처**: [[07.command-domain-jpa-separation]]
- **옵션**: 공통 매퍼 규약 / 매핑 함수 컨벤션 / MapStruct 등 도구
- **쟁점**: 분리 유지의 대가인 매핑 비용을 어떻게 표준화·축소하나(도구 도입은 신중).

### Q3. Strangler 전환 순서 확정 (현재 "초안 — 의존성 기반")
- **출처**: [[06.strangler-migration]] · [[04-migration]]
- **쟁점**: 1~5단계 순서가 초안. 의존성·위험·학습 가치 기준으로 첫 전환 컨텍스트와 순서를 확정.

### Q4. 단계별 사이클 분할·일정
- **출처**: [[06.strangler-migration]] · [[04-migration]]
- **쟁점**: 이 사이클은 설계까지. 각 전환 단계를 어떤 단위의 별도 사이클로 쪼개고 어떤 순서로 도는지.

### Q5. 신규 기능(리뷰/별점·포인트·신고) 컨텍스트화 투입 시점·설계
- **출처**: [[RFC-001-v2-cqrs-and-event-sourcing]] · [[06.strangler-migration]] · [[04-migration]]
- **쟁점**: "전환된 패턴 위에서 추가"라는 방향만 있음. 별도 컨텍스트로 둘지, 어느 전환 단계 이후 투입하는지.

> command/query **물리 배포** 분리 시점은 [[RFC-008-deployment-infra-ops]] Q2에서 다룬다(여기선 모듈 분리=논리까지).

## 닫는 방식

- Q1·Q2·Q3·Q4·Q5 = **논의로 지금 결정**.
- 🌱 없음 — 단, Q5의 신규 기능 상세 설계는 카탈로그·전환 진행에 따라 후속 사이클.

## 산출물

- [[01-module-structure]] §물리 배치·매핑 규약 확정.
- [[04-migration]] §전환 순서(초안) → 확정.
- [[06.strangler-migration]]·[[07.command-domain-jpa-separation]] TBD 해소 → 비준.

## 관련 문서
- [[RFC-002-decision-queue]] · [[01-module-structure]] · [[04-migration]] · [[06.strangler-migration]] · [[07.command-domain-jpa-separation]] · [[RFC-008-deployment-infra-ops]]
