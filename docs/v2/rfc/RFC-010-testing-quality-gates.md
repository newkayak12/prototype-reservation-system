# RFC-010 — 테스트·품질 게이트

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[11-environments-and-testing]] 보강 + [[14.testing-strategy]] 비준 (Proposed→Accepted)

## 배경

5개 테스트 범주 — 아키텍처 강제(1순위: query↛command·도메인↛JPA·command↔query는 이벤트로만), property-based(Fixture Monkey), 소비자 계약, 업캐스팅 회귀, Chaos Monkey — 의 *범주*는 라운드1에서 정했다. 남은 건 도구 택일과 게이트 임계다. 여기서 닫는다.

## 논의 항목

### Q1. 아키텍처 강제 도구 (ArchUnit vs Konsist)
- **출처**: [[14.testing-strategy]] · [[01.cqrs-command-query-module-split]] · [[01-module-structure]]
- **옵션**: (a) ArchUnit / (b) Konsist
- **쟁점**: Konsist가 Kotlin 네이티브(코틀린 DSL·심볼 기반)라 유력. ArchUnit은 성숙·JVM 표준. 경계 규칙(query↛command 등)을 어느 쪽으로 강제하나.

### Q2. 소비자 계약 도구 (Pact vs Spring Cloud Contract)
- **출처**: [[14.testing-strategy]]
- **옵션**: (a) Pact / (b) Spring Cloud Contract
- **쟁점**: 이벤트 스키마(생산자)↔컨슈머 계약을 어느 도구로. Spring 생태계 친화 vs Pact 브로커 생태계.

### Q3. 카오스 인프라 레벨 도구
- **출처**: [[14.testing-strategy]] · [[11-environments-and-testing]]
- **옵션**: (a) 수동 kill / (b) Chaos Mesh / (c) Litmus
- **쟁점**: 앱 레벨은 **Chaos Monkey for Spring Boot로 확정**. 인프라(파드/네트워크) 레벨 주입 도구는 T-05 확장 시 도입 — 그 트리거를 정의.

### Q4. 각 테스트 범주 커버리지 목표·CI 게이트화 임계
- **출처**: [[14.testing-strategy]] · [[11-environments-and-testing]]
- **측정 트리거**: 게이트 *정책*(어느 범주를 CI 필수 게이트로 묶나)은 지금, 커버리지 임계 숫자는 베이스라인 측정 후.

### Q5. k6 절대 SLO·CI 자동 게이트화 임계
- **출처**: [[11-environments-and-testing]] · [[14.testing-strategy]]
- **측정 트리거**: 부하 테스트를 CI 게이트로 둘지 *정책*은 지금, 절대 SLO·임계는 베이스라인 측정 확보 후.

### Q6. localstack로 검증할 AWS 서비스 최종 목록
- **출처**: [[11-environments-and-testing]]
- **쟁점**: 컨텍스트 전환 시 실제 의존이 확정돼야 목록이 닫힘. 콜드 스토리지 S3 검증은 [[RFC-005-event-store-schema-evolution]] 매체 결정(아카이브 테이블 vs 오브젝트 스토리지)에 의존.

## 닫는 방식

- Q1·Q2·Q3·Q6 = **논의로 지금 결정**(도구 택일·트리거; Q6는 의존 RFC 결정 후 확정).
- Q4·Q5 = **측정 트리거**(게이트 정책 지금, 임계 숫자 베이스라인 후).
- 🌱 없음.

## 산출물

- [[11-environments-and-testing]] §5.5·§결정·미결정 해소(도구·게이트 정책).
- [[14.testing-strategy]] 미결정 섹션 해소 → `Proposed`→`Accepted`.

## 관련 문서
- [[RFC-002-decision-queue]] · [[14.testing-strategy]] · [[11-environments-and-testing]] · [[RFC-005-event-store-schema-evolution]]
