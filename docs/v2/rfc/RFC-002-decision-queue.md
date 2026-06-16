# RFC-002 — V2 결정 큐 (라운드2 인덱스)

- **상태**: Open (작업 큐) · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]]
- **성격**: 인덱스. 라운드1이 못 정한 구체 결정을 주제별 RFC(003~014)로 쪼개 닫는다.
- **순서**: 아래 표는 **우선순위 순**(EDA/ES 코어 → 전환 실무 → K8s/운영 → 품질). 파일 번호는 안정 ID라 순서와 무관 — 정독은 이 표 순서대로.

## 닫는 방식 (범례)

- **논의** — 토론으로 지금 결정 가능 (대부분).
- **측정 트리거** — *정책은 지금* 결정(초기값 + 재검토 조건), *숫자는* 운영/부하 측정으로 튜닝. "미룸"이 아니다.
- 🌱 **스토밍 선행** — 이벤트가 뭔지 모르면 책상에서 못 정함. 이벤트 스토밍 후. (전체에서 4건뿐)

## 주제 RFC

| # | RFC | 주제 | 항목 | 닫으면 보강할 곳 |
|---|---|---|---|---|
| 1 | [[RFC-005-event-store-schema-evolution]] | 이벤트 스토어·스키마 진화 | 12 | [[08-event-store-lifecycle]]·[[05.event-store-mysql-table]]·[[10.event-schema-evolution]] |
| 2 | [[RFC-004-messaging-delivery]] | 메시징·전달 보장 | 7 | [[07-messaging-topology]]·[[09.event-ordering-and-delivery-guarantee]] |
| 3 | [[RFC-012-projection-rebuild-catchup]] | 프로젝션 재구축·catch-up 운영 | 6 | [[03-read-model]] |
| 4 | [[RFC-003-read-model-consistency]] | 읽기 모델·일관성 | 7 | [[03-read-model]]·[[04.read-model-projection-and-replica]] |
| 5 | [[RFC-007-saga-process-manager]] | Saga·프로세스 매니저 | 4 | [[06-consistency-and-sagas]]·[[08.saga-orchestration-vs-choreography]] |
| 6 | [[RFC-013-command-query-api-contract]] | command/query API 계약·비동기 command | 6 | 신규 design_doc + ADR |
| 7 | [[RFC-006-pii-security]] | PII·보안 | 6 | [[11.es-pii-crypto-shredding]] |
| 8 | [[RFC-011-module-structure-migration]] | 모듈 구조·마이그레이션 확정 | 5 | [[01-module-structure]]·[[04-migration]]·[[06.strangler-migration]]·[[07.command-domain-jpa-separation]] |
| 9 | [[RFC-014-data-migration-genesis-events]] | V1→V2 데이터 이행(제네시스 이벤트) | 5 | [[04-migration]] |
| 10 | [[RFC-008-deployment-infra-ops]] | 배포·인프라·운영 (K8s/Strimzi) | 12 | [[09-deployment-runtime]]·[[12.kafka-hosting-msk-vs-self-managed]]·[[13.db-hosting-and-read-write-topology]] |
| 11 | [[RFC-009-observability]] | 관측성 | 6 | [[10-observability]] |
| 12 | [[RFC-010-testing-quality-gates]] | 테스트·품질 게이트 | 6 | [[11-environments-and-testing]]·[[14.testing-strategy]] |

> 각 RFC 본문이 **그 주제의 미결 전체 + 옵션 + 출처**를 담는다. 이 표는 지도일 뿐.

## 라운드3 — 기존 RFC가 매달아 둔 실 + 아키텍처 결정면 보강

라운드2(003~014)가 V2 결정면의 *큰 틀*을 덮은 뒤, 그 RFC들이 **열어놓고 안 닫은 실**과 **다른 트랙에 있던 항목 중 아키텍처 결정면인 슬라이스**를 별도 RFC로 추가한다.

| # | RFC | 주제 | 어디서 매달려 왔나 | 닫으면 보강할 곳 |
|---|---|---|---|---|
| 13 | [[RFC-015-aggregate-concurrency-control]] | 애그리거트 동시성·쓰기 경합 제어 | [[RFC-013-command-query-api-contract]] 낙관 락·[[RFC-007-saga-process-manager]] 임시 점유가 *언급*만 함 | [[05-aggregate-design]] + 신규 ADR |
| 14 | [[RFC-016-authorization-model]] | V2 인가 모델 | [[RFC-013-command-query-api-contract]]이 인증서버·게이트웨이를 토대로만 두고 *인가 모델*은 미정 | 신규 design_doc + ADR |
| 15 | [[RFC-017-payment-integration-boundary]] | 결제 연동 경계 (payment ACL) | [[RFC-007-saga-process-manager]]이 `payment` 컨텍스트·결제 이벤트를 새로 끌어들임 | [[06-consistency-and-sagas]] + 신규 design_doc·ADR |
| 16 | [[RFC-018-disaster-recovery-event-store]] | 재해 복구·이벤트 스토어 복구 의미론 | T-18 DR 런북 중 *아키텍처 복구 의미론* 슬라이스 (운영 수치는 T-18 잔류) | [[08-event-store-lifecycle]] + 신규 ADR |
| 17 | [[RFC-019-caching-redis-role]] | 캐싱·Redis의 V2 역할 | 호스팅(투명)과 별개로 *투영 위 캐시가 필요한가*는 아키텍처 질문 | [[03-read-model]] + 신규 ADR |

> 라운드3은 **신규 결정면이 아니라 기존 결정의 하류·경계**다. 의도적으로 다른 트랙에 둔 것(이벤트 스토밍·알림·운영 런북·호스팅)은 아래 "RFC에 없는 것"에 그대로 남긴다.

## RFC에 없는 것 (다른 트랙)

RFC-003~014가 **V2 아키텍처 결정면 전체**를 덮는다. 그 바깥은 RFC가 아니라 다른 트랙에서 닫힌다 — 여기 한눈에 모은다.

### 🌱 도메인 모델링 트랙 → 이벤트 스토밍 / 도메인 사이클
RFC가 아니라 **워크숍 + 별도 사이클**. 이게 풀려야 각 RFC의 🌱 항목(토픽 목록·ES 경계·PII 필드 분류·예약 외 흐름 분류)이 닫힌다.
- **컨텍스트별 도메인 이벤트 카탈로그**(목록·페이로드·버전) — 이벤트 스토밍 재실시 선행.
- **DDD 심화 체크리스트**([[index|docs/todo]] T-21) — 애그리거트 경계 휴리스틱·불변식 배치·컨텍스트 매핑·Specification·VO 심화·도메인 서비스 triage·불변 전이·Command/Event 명명·유비쿼터스 언어·Example Mapping·Policy/PM·낙관 락. → design_doc [[05-aggregate-design]] + 도메인 사이클 입력.

### 운영 백로그 → [[index|docs/todo]] (순수 운영, 아키텍처 영향 없음)
- T-03 Docker 정리 / T-11 CI/CD·GitOps(ArgoCD/Flux·progressive delivery·Flyway-in-k8s·이미지 스캐닝/SBOM) / T-12 SLI·**SLO 목표값** / T-13 시크릿 관리·인증 확장(Vault/Secrets Manager·OAuth2/OIDC) / T-18 **DR 런북**.
- T-19 **KEDA**(consumer lag 기반 오토스케일링) — 운영 성숙 시 [[RFC-008-deployment-infra-ops]]로 역류 가능.

### 호스팅 선택 → 배포 사이클
- RDS vs 자가 / ElastiCache vs 자가 — 호스팅 형태 투명 원칙. 앱 무관, 운영 비용으로 판단([[RFC-008-deployment-infra-ops]] §위임).

### 별도 스레드 (V2 아키텍처 아님)
- `.claude/settings.json` — 하네스/Claude 설정. "같이 논의" 대기 항목이나 V2 RFC 큐 아님.

### 아직 안 한 스윕
- **세션 기록에만 있고 문서에 안 내려간 결정/곁가지** — doc grep으로 안 잡힘. 필요 시 별도 회수.

## 메타 — ADR 비준

ADR **08~14가 전부 `Proposed`**(미비준)다. 각 주제 RFC를 닫을 때, 관련 ADR을 함께 `Accepted`로 승급한다(또는 개정). 비준 자체가 결정 큐의 일부.

## 진행 규칙

- **WIP=1 권장** — 한 번에 한 주제 RFC만 연다(강결합이면 함께: 프로젝션 운영↔읽기 모델, 마이그레이션↔데이터 이행).
- **순서 = 위 표 1→12**: 이벤트 스토어 → 메시징 → 프로젝션 운영 → 읽기 모델 → Saga → API 계약 → PII → 모듈·마이그레이션 → 데이터 이행 → 배포(K8s) → 관측성 → 테스트.
- 한 RFC가 닫히면: design_doc 보강 + 신규/개정 ADR + 본문 상태 `Resolved`.

## 관련 문서
- [[RFC-001-v2-cqrs-and-event-sourcing]] · [[index]]
