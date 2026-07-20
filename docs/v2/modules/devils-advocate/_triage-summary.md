# Devil's Advocate 트리아지 요약

> 13개 모듈 문서(00~12) devil's-advocate 검증 결과의 최종 상태. 각 리포트 원본은 같은 디렉터리의 `<번호>-<모듈명>.md` 참조.
> 최종 갱신: 2026-07-20

## 범례

- **해소** — RFC/ADR 합의 반영, 코드/문서 수정, 또는 규칙(Konsist) 추가로 실제로 닫힘.
- **자연해소 불가** — 시간이 지나거나 구현을 진행한다고 저절로 풀리지 않음. 누군가 명시적으로 결정·측정·구현해야 닫힘.
- **부분 해소** — 표현/구조 문제는 닫혔지만 근본 리스크(주로 미측정 성능·용량)는 남음.

## 오늘(2026-07-19~20) 해소된 항목

| 파일 | 반론 | 근거 |
|---|---|---|
| 00-module-index | 반론2(강제 메커니즘 없음) | Konsist 도입([[RFC-031]])으로 import 기반 우회는 잡힘. 단 타입 판별자 문자열 분기는 여전히 못 잡음(아래 "자연해소 불가" 참조) |
| 01-shared-module | 핵심취약점(positive 기준 없음) | §1.1 포함 기준 3개 명문화 + Konsist R7(프레임워크 순수성)로 기준 1 강제 |
| 02-contract-module | 반론1(RFC-029 미반영) | event-carried 일원화 반영 |
| 03-command-core | 반론(shared 전이 의존 미검증) | Konsist R7로 shared-module도 게이트됨 |
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

| 파일 | 남은 반론 | 왜 자연해소 안 되는가 |
|---|---|---|
| 00-module-index | 타입 판별자(`eventType` 문자열/enum) 기반 런타임 분기 우회 | Gradle·Konsist 둘 다 정적 import 분석 — 데이터 값 기반 분기는 원리상 못 잡음. 코드 리뷰 컨벤션으로만 막힘(RFC-031에 한계로 명시) |
| 03-command-core | R2(9+ 서브도메인 컨텍스트 격리 규칙의 유지보수 트리거 부재) | 신규 도메인 추가 시 규칙을 누가 갱신하는지 프로세스가 없으면 도메인이 늘어날수록 누락 위험만 커짐 |
| 05-command-adapter | 반론2(pre-authenticated 헤더 위조 SPOF) | mTLS/NetworkPolicy 같은 물리적 강제가 실제 설정으로 존재해야 닫힘 — [[DESIGN-010]]·[[ADR-024]] 구현 사안 |
| 06-command-infrastructure | 반론2(event_store+outbox 동일 datasource one-way door) | RFC-025가 Non-goal로 명시 유예("구현 시 확인") — 저장소 분리 필요성이 실증되기 전엔 결정 자체가 없음 |
| 07/08(query) | DB 쓰기 상한 미측정(LWW 가드 행 잠금 경합), freshness gate 대기 상한 미정 | k6 실측 필요 — 측정 없이는 숫자가 안 생김. [[12-implementation-plan]] C-6과 동일 사안 |
| 09-auth-server-module | 다중세션 컬럼 설계, 재사용탐지 로그아웃 폭탄, 헤더위조 SPOF | 전부 제품/보안 정책 결정 — 폰+웹 동시 세션을 지원할지, 오탐 허용치를 얼마로 할지는 팀이 정해야 함 |
| 10-test-module | 반론2(공유 픽스처 결합) | 레이어별 픽스처 격리 전략(빌더 기본값 캡슐화 등)을 실제로 설계해야 닫힘 |
| 11-runtime-topology | C1(물리 분리 트리거 기준 없음), 네트워크 격리 미검증 | 트리거 임계값·NetworkPolicy를 누가 정의해야 생김 — 정의 안 하면 영원히 "언젠가"로 남음 |
| 12-implementation-plan | C-1(동일 datasource), C-2(다중소스 원자성), C-6(쓰기 병목) | 06의 datasource 이슈와 동일 사안(측정/명문화 필요), 스케줄에 게이트 없음 |

## 부분 해소 — 표현은 정리됐지만 근본 리스크는 남음

| 파일 | 내용 |
|---|---|
| 07-query-projection-server | "병렬 상한=파티션 수" vs "Parallel Consumer 돌파"는 서로 다른 축(인스턴스 내부/간)임을 명시해 표현 충돌은 닫혔으나, DB 쓰기 실측은 위 표대로 여전히 미해소 |
| 08-query-read-model-server | read-after-write 계약은 반영됐으나, `ReadFreshnessGate`의 대기 상한·타임아웃 수치는 구현 시 결정 필요 |

## 교차 발견 (오늘 세션에서 새로 드러남)

- [[RFC-031]](ArchUnit 채택안)이 이미 합의된 [[RFC-009-testing-quality-gates]](Konsist 채택)를 인지하지 못하고 반대 방향으로 작성된 RFC 간 drift였음 — Konsist로 정정.
- `DESIGN-021-architecture-fitness-functions-konsist.md`가 별도 worktree에서 동시에 작성되었다가 사용자가 삭제 — RFC-031이 규칙 카탈로그의 단독 소스로 남음.
- [[12-implementation-plan]]의 C-3·C-4·C-5·C-7·M-9가 이미 합의된 RFC들로 해소됐는데도 "미결" 표기가 갱신되지 않고 있었음(오늘 수정).
