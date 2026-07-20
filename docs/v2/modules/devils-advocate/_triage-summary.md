# Devil's Advocate 트리아지 요약

> 13개 모듈 문서(00~12) devil's-advocate 검증 결과의 최종 상태. 각 리포트 원본은 같은 디렉터리의 `<번호>-<모듈명>.md` 참조.
> 최종 갱신: 2026-07-20

## 범례

- **해소** — RFC/ADR 합의 반영, 코드/문서 수정, 규칙(Konsist) 추가 등 **실제 조치**로 닫힘.
- **자연해소 불가** — 시간이 지나거나 구현을 진행한다고 저절로 안 풀림. 누군가 명시적으로 결정·측정·구현해야 닫힘.
- **부분 해소** — 표현/구조 문제는 닫혔지만 근본 리스크(주로 미측정 성능·용량)는 남음.
- **도구 한계 · 오분류** — 애초에 "미결"이 아니었던 것. 도구의 영구 경계이거나, devils-advocate 추정이 미결 요구로 잘못 승격된 것. 위 표들과 같은 무게로 추적하지 않는다.

## 해소된 항목 (2026-07-19~20)

| 파일 | 반론 | 근거 |
|---|---|---|
| 00-module-index | 반론2(강제 메커니즘 없음) | Konsist 도입([[RFC-031]])으로 import 기반 우회는 잡힘. 타입 판별자 문자열 분기는 §도구 한계·오분류 참조 |
| 01-shared-module | 핵심취약점(positive 기준 없음) | §1.1 포함 기준 3개 명문화 + Konsist R7(프레임워크 순수성)로 기준 1 강제 |
| 02-contract-module | 반론1(RFC-029 미반영) | event-carried 일원화 반영 |
| 03-command-core | 반론(shared 전이 의존 미검증) | Konsist R7로 shared-module도 게이트됨 |
| 03-command-core | R2(컨텍스트 격리 규칙 유지보수 트리거 부재) | [[RFC-031]] R3를 쌍 열거 대신 "`support` 제외 도메인 목록 동적 순회" 일반 규칙으로 재작성 — 신규 도메인은 자동 편입, 규칙 수동 갱신 불필요. 설계로 해소(프로세스 미결이 아니었음) |
| 04-command-application | 반론1·2(동시성 방향 모순) | 비관 락(Redisson L1+DB 폴백)+UNIQUE 백스톱 확정([[RFC-014]]·[[ADR-016]]) |
| 05-command-adapter | 반론1(예외 계약 미정의) | `AggregateConflictException` 번역 계약을 04/05 양쪽에 명시 |
| 06-command-infrastructure | 반론1(SKIP LOCKED vs 순서 모순) | ShedLock 단일 relay로 갱신([[RFC-025]]) |
| 07-query-projection-server | 반론1·2(RFC-025 미반영), 반론3 표현 충돌 | LWW seq 가드·DLQ 정책 갱신, 스케일 축 2개 구분 명시 |
| 08-query-read-model-server | 반론1(RFC-030 미반영) | `sequenceNo`+`ReadFreshnessGate` 반영 |
| 09-auth-server-module | 핵심취약점(SAS/jjwt 은폐된 기정사실화) | SAS 채택 확정([[RFC-020]]·[[ADR-024]]) |
| 10-test-module | 반론1(ADR-014 모순) | Application도 Kotest `BehaviorSpec`으로 통일 |
| 12-implementation-plan | C-3·C-4·C-5·C-7·M-9 stale | 위 RFC/ADR 합의를 반영해 표 갱신 |

## 자연해소 불가 — 명시적 결정/측정 필요

시간이 지나거나 구현하다 보면 저절로 안 풀리는 항목. 담당·시점을 정해야 닫힌다.

| 파일 | 남은 반론 | 왜 자연해소 안 되는가 · 현재 경로 |
|---|---|---|
| 05-command-adapter | 반론2(pre-authenticated 헤더 위조 SPOF) | mTLS/NetworkPolicy 같은 물리적 강제가 실제 설정으로 있어야 닫힘. 엣지 방식은 [[07-k8s-edge-gateway-study]]에서 검토 중(Envoy Gateway + NetworkPolicy가 완화책) — 게이트웨이 확정 후 [[ADR-024]]·[[DESIGN-010]]에 반영 |
| 06-command-infrastructure | 반론2(event_store+outbox 동일 datasource one-way door) | RFC-025가 Non-goal로 유예("구현 시 확인"). 저장소 분리 필요성이 실증되기 전엔 결정 자체가 없음. **12-plan C-1과 동일 사안** |
| 07/08(query) | DB 쓰기 상한 미측정(LWW 가드 행 경합), freshness gate 대기 상한 미정 | capacity 상한은 목표치(SLO) 없이도 k6로 **측정 가능** — 방법은 [[08-k6-load-test-strategy]]에 확정, 실측만 남음. gate 대기 상한은 그 측정의 lag 분포에서 파생. "상한이 충분한가"의 SLO 판정은 실트래픽이 없어 유예가 정상(지어내지 않음) |
| 09-auth-server-module | 재사용탐지 로그아웃 폭탄, 헤더위조 SPOF | 제품/보안 정책 결정 — 재사용탐지 오탐 허용치를 팀이 정해야 함 |
| 11-runtime-topology | C1(물리 분리 트리거 기준 없음), 네트워크 격리 미검증 | 트리거 임계값·NetworkPolicy를 정의해야 생김. 네트워크 격리는 05 헤더위조 완화·[[07-k8s-edge-gateway-study]]와 연결 |
| 12-implementation-plan | C-1(동일 datasource), C-2(다중소스 원자성), C-6(쓰기 병목) | C-1=06 반론2와 동일(측정/명문화), C-6=07/08 쓰기 상한과 동일(k6). C-2는 부분 갱신을 정상으로 받아들일지 첫 레퍼런스에서 확정 필요. 공통 리스크: **일정에 이 결정들을 강제하는 게이트가 없음** — one-way door(C-1)를 싼 시점에 못 박지 못하고 지나칠 위험 |

## 부분 해소 — 표현은 정리됐지만 근본 리스크는 남음

| 파일 | 내용 |
|---|---|
| 07-query-projection-server | 스케일 축 2개(인스턴스 내부/간)를 구분해 표현 충돌은 닫혔으나, DB 쓰기 실측은 위 표대로 미해소([[08-k6-load-test-strategy]] 시나리오 B) |
| 08-query-read-model-server | read-after-write 계약은 반영됐으나 `ReadFreshnessGate` 대기 상한·타임아웃 수치는 구현 시 결정([[08-k6-load-test-strategy]] 시나리오 C가 대기 분포를 측정) |

## 도구 한계 · 오분류 — 조치 불필요 ("미결" 아님)

애초에 "누군가 결정해야 닫히는 미결"이 아니었던 것들. 재검증으로 걸러냈다.

| 파일 | 항목 | 왜 조치가 필요 없는가 |
|---|---|---|
| 00-module-index | 타입 판별자(`eventType`) 런타임 분기 우회 | Gradle·Konsist 둘 다 정적 import 분석이라 데이터 값 분기는 원리상 못 잡음 — 어떤 정적분석 도구를 써도 마찬가지(도구 선택 문제 아님). [[RFC-031]] R6에 한계로 기록됨. 유일한 대응은 "이 패턴 안 씀"이고 이미 지킴 |
| 09-auth-server-module | 다중세션(멀티디바이스) 요구 부재 | 사용자가 요구한 적 없음 — devils-advocate의 "예약 도메인은 보통 모바일+웹 동시 사용"이라는 추정이 미결 요구로 승격됐던 것. 단일 세션(`current_refresh_jti` 단일 컬럼)이 **의도된 설계**. 안 원하는 걸 봉쇄한 것은 결함이 아니라 제약 |
| 10-test-module | 공유 픽스처 단일 실패점(원문 반론3, 트리아지엔 "반론2"로 오기됐었음) | 실제 `test-module`은 `contract-module` 의존이 없고 도메인/이벤트 픽스처도 없음 — `CommonlyUsedArbitraries`(원시 값 생성기)+`FixtureMonkeyFactory`(제네릭 팩토리)뿐인 salt. "계약 한 필드 변경→전 계층 붉어짐"의 전제(도메인 픽스처 중앙화+contract 의존)가 성립 안 함. 이미 격리된 구조 |

## 교차 발견 (오늘 세션에서 새로 드러남)

- [[RFC-031]](ArchUnit 채택안)이 이미 합의된 [[RFC-009-testing-quality-gates]](Konsist 채택)를 인지 못하고 반대 방향으로 작성된 RFC 간 drift였음 — Konsist로 정정.
- `DESIGN-021-architecture-fitness-functions-konsist.md`가 별도 worktree에서 동시 작성됐다가 사용자가 삭제 — RFC-031이 규칙 카탈로그 단독 소스로 남음.
- [[12-implementation-plan]]의 C-3·C-4·C-5·C-7·M-9가 이미 합의된 RFC들로 해소됐는데도 "미결" 표기가 갱신 안 되고 있었음(오늘 수정).
- **"자연해소 불가" 판정 자체가 재검증 대상이었다.** 이 표에 올려놨던 4건이 재검증 결과 미결이 아니었다 — eventType 우회(도구의 영구 경계)·10-test-module 픽스처(코드가 전제를 반박)·03-command-core R2(일반 규칙으로 재설계하면 소멸)·09-auth 다중세션(사용자가 요구한 적 없는 devils-advocate 추정). 교훈: 반론을 "미결 요구"로 옮겨 적기 전에 (a) 사용자가 실제로 요구했는지, (b) 실제 코드/구조가 전제를 뒷받침하는지 먼저 확인해야 한다([[ai-draft-laundered-as-user-decision]]).
