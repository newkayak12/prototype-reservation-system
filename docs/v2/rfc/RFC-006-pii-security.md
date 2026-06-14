# RFC-006 — PII·보안

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[11.es-pii-crypto-shredding]] 보강·비준 (Proposed→Accepted)

## 배경

append-only 이벤트 스토어는 물리 삭제가 불가하다 → GDPR 삭제권은 **크립토 셰딩**(이벤트엔 암호문, 주체별 키 별도 보관, 키 삭제=셰딩)으로 충족하고 동시에 **PII 최소화**를 적용한다. 메커니즘 자체는 라운드1에서 잠갔으나 **키 운영·PII 분류·셰딩 토큰 표현**은 미결로 남았다. 여기서 닫는다.

## 논의 항목

### Q1. 키 보관처
- **출처**: [[11.es-pii-crypto-shredding]]
- **옵션**: (a) KMS 도입 / (b) 자체 `encryption_key` 테이블(V1 자산 재활용)
- **쟁점**: 운영 복잡도·키 격리 수준 vs 기존 자산 재활용. 셰딩 단위(주체별 키)와 정합. → **논의로 결정**.

### Q2. 🌱 PII 필드 분류 (어떤 필드가 PII인가)
- **출처**: [[11.es-pii-crypto-shredding]] · [[08-event-store-lifecycle]]
- **쟁점**: 암호화·셰딩 대상 필드 집합을 컨텍스트별로 확정. 분류가 없으면 셰딩 범위가 미정.
- **선행**: 🌱 이벤트 카탈로그 스토밍/도메인 작업이 선행돼야 분류 가능.

### Q3. 셰딩 누락 방지 가드
- **출처**: [[11.es-pii-crypto-shredding]]
- **옵션**: (a) ArchUnit/Konsist 규칙 / (b) 리뷰 / (c) 스키마 어노테이션
- **쟁점**: PII 필드가 암호화 경로를 우회하지 못하게 강제. [[14.testing-strategy]] ArchUnit과 연계. → **논의로 결정**.

### Q4. 키 로테이션 정책 + 키 백업·접근 통제
- **출처**: [[11.es-pii-crypto-shredding]]
- **쟁점**: 로테이션 주기·방식 그리고 키 자체의 백업·접근 통제(셰딩된 키가 백업에서 부활하면 셰딩 무효). 정책 지금, 운영 수치는 운영시. → **논의/측정**.

### Q5. 셰딩 토큰 표현 + 다운스트림 프로젝션 갱신
- **출처**: [[11.es-pii-crypto-shredding]]
- **옵션**: 복호 실패 시 표현 — (a) `"[redacted]"` / (b) `null` / (c) 익명 토큰
- **쟁점**: 셰딩 후 다운스트림 프로젝션을 어떻게 갱신/재구성하는지(재생 시 복호 실패 처리)와 일관. → **논의로 결정**.

### Q6. 콜드 스토리지 이관 이벤트의 셰딩 경로
- **출처**: [[11.es-pii-crypto-shredding]]
- **쟁점**: 콜드 스토리지로 이관된 이벤트도 키 삭제 한 번으로 셰딩되는지(매체별 키 적용 경로 보장). [[RFC-005-event-store-schema-evolution]] 콜드 스토리지 매체 결정과 연계. → **논의로 결정**.

## 닫는 방식

- Q1·Q3·Q5·Q6 = **논의로 지금 결정**.
- Q4 = **논의/측정**(정책 지금, 수치 운영시).
- 🌱 Q2 = 이벤트 카탈로그 스토밍/도메인 작업 선행 후 확정.

## 산출물

- [[11.es-pii-crypto-shredding]] 미결 섹션(키 보관처·분류·가드·로테이션·토큰·콜드 셰딩) 해소 → `Proposed`→`Accepted` 비준.
- 필요 시 신규 ADR(예: "PII 분류 정책", "키 로테이션·접근 통제 정책").

## 관련 문서
- [[RFC-002-decision-queue]] · [[11.es-pii-crypto-shredding]] · [[08-event-store-lifecycle]] · [[14.testing-strategy]] · [[RFC-005-event-store-schema-evolution]]
