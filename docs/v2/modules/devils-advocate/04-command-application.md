# Devil's Advocate — 04-command-application

> 대상: [[04-command-application]] · 방법론: position 명료화 → steel-man → 숨은 가정 → 반론 → (해당 시) 다중 페르소나 → 핵심 취약점 → 가역성.
> 근거로 인용한 원문: [[DESIGN-019-event-execution-layering]], [[DESIGN-003-write-model]], [[DESIGN-009-event-store-lifecycle]].

## 1. Position + Steel-man

**Position**: core 이벤트 타입을 아는 유일한 계층인 command-application이 rehydrate·직렬화·core→contract 매핑·append+outbox 단일 트랜잭션을 전부 소유한다.

**Steel-man**: 타입 소유를 한 계층에 모으면 `infra ↛ core` 불변식이 컴파일 타임에 물리적으로 강제되고, append와 replay가 대칭적으로 같은 자리에서 닫혀 경계 누수를 설계로 원천 차단한다 — 조건은 이벤트 스트림이 짧고(스냅샷 불필요), event_store·outbox가 같은 datasource이며, aggregate당 동시 쓰기가 드물 때다.

## 2. 숨은 가정

1. event_store와 outbox가 **동일 datasource**라 `@Transactional` 하나로 원자성이 성립한다 — 그러나 [[DESIGN-019-event-execution-layering]] §9는 이를 "구현 시 확인"해야 할 미해결 항목(C06)으로 명시한다. 이 문서(04)는 그 미확인 전제를 §5 코드 예시에서 이미 확정된 것처럼 쓴다.
2. core→contract 매퍼와 직렬화는 부작용 없는 순수 변환이라 트랜잭션 경계 안에 넣어도 트랜잭션이 길어지지 않는다 — 매퍼/직렬화 라이브러리(Jackson)의 예외·성능 특성이 트랜잭션 지속시간에 영향 없다는 것은 검증되지 않았다.
3. 리플레이(load→역직렬화→fold) 비용은 크리티컬 패스에 넣을 만큼 무시할 수 있다 — 그러나 [[DESIGN-009-event-store-lifecycle]]은 스냅샷 로드 경로(§3.1, load→스냅샷 존재 확인→state 복원)를 이미 **Accepted 메커니즘**으로 설계해 두었다. "미래 최적화"가 아니라 이미 확정된 메커니즘을 04번 문서의 구조(§4)와 코드 예시(§5)가 전혀 참조하지 않는다.

## 3. 반론

1. `[structural]` · **severity: high** — 포트 시그니처가 동시성 가드를 표현하지 못한다. `EventStorePort.append(events: List<StoredEvent>)`에는 `expectedVersion`/`expectedSeq` 파라미터가 없다. 그런데 §5 `CancelReservationService.cancel()`은 락도 버전 체크도 없이 `load→fold→handle→append`만 수행한다. 같은 aggregate에 대한 두 동시 취소 요청은 lost update이거나, DB의 `UNIQUE(aggregate_id, sequence_no)`([[DESIGN-003-write-model]] line 60, 64) 위반으로 raw `DataIntegrityViolationException`이 튀어 오른다. 이 예외는 타입도 도메인 의미도 없는 infra 신호인데, 이는 "infra는 bytes만 알고 타입 신호를 절대 내지 않는다"는 이 문서의 핵심 불변식을 실행 시점에 무력화한다. **선례**: [[DESIGN-003-write-model]] line 82·147 — UNIQUE는 safety 백스톱, Redisson(L1)이 정상 경로임을 명시하는데 04번 문서의 쓰기 경로 샘플은 그 둘을 모두 뺐다.

2. `[assumption]` · **severity: high** — 이 문서의 동시성 방향이 근거 문서 내부에서도 확정되지 않은 사안을 이미 정해진 것처럼 다룬다. §3 라이브러리 표는 `spring-retry`를 "낙관적 append 충돌 시 bounded 재시도"로 올려 락-프리 낙관 경로를 시사한다. [[DESIGN-003-write-model]] line 133은 애초 "낙관적 락만 사용" 방향을 이미 한 차례 **비관 락(Redisson)+UNIQUE로 개정**한 바 있다("낙관적 락은 충돌 시 재시도 부담이 예약 컨텍스트에서 과함"). 이후 같은 문서 자기리뷰 line 188–190에서 그 개정 자체를 다시 문제 삼아 "aggregate당 동시 쓰기가 드문 흐름(취소류)은 락-프리 낙관, 경합 높은 흐름은 선택적 비관"이라는 **도메인별 혼용 방향**을 제안했지만 이는 "k6 실측 후 확정 권장"이라는 조건부 제안이지 확정이 아니다. 즉 04번 문서는 세 상태(원 결정=비관 · 재검토=혼용 제안 · 미확정) 중 어느 것도 아닌 "낙관 전용" 뉘앙스를 코드·라이브러리 표에 선반영했고, §5 코드에는 낙관·비관 어느 가드도 실제로 넣지 않았다. 세 곳(코드·라이브러리 표·근거 문서)이 서로 다른 상태를 가리킨다. **선례**: [[DESIGN-003-write-model]] line 133 vs line 190.

3. `[execution]` · **severity: medium** — 하나의 `@Service`가 rehydrate·fold·직렬화·매핑·트랜잭션을 전부 짊어진다. 취소 한 건마다 전체 이벤트 스트림을 load→역직렬화→fold 하므로 지연이 이벤트 수에 선형이며, [[DESIGN-009-event-store-lifecycle]]이 이미 설계해 둔 스냅샷 로드 경로를 04번 문서의 구조(§4 `AggregateRehydrator`)와 코드 예시(§5)가 반영하지 않는다 — "스냅샷 최적화는 나중"이라는 정당화가 실제로는 이미 Accepted된 메커니즘을 누락시킨 것이다. 또한 단위 테스트는 포트 4개 목킹 × 직렬화 레지스트리 × 매퍼 × 리플레이 상태 조합으로 폭발할 소지가 있다. "매핑·직렬화의 자연한 자리"라는 정당화는 *타입을 쥔다 = 실행 책임을 전부 진다*를 암묵적으로 등치했지만, 타입 소유(불변식 보호)와 오케스트레이션 비대(SRP 위반)는 분리 가능한 문제다. **선례**: no clear precedent — 리플레이 비용은 실측 전이라 speculative concern.

## 4. 다중 페르소나 공격

**온콜 엔지니어**: 새벽에 취소 API가 409/500을 뱉는다는 알람이 온다. 스택트레이스는 `DataIntegrityViolationException: Duplicate entry ... for key 'uk_aggregate_seq'` — 도메인 용어가 하나도 없다. 이 예외가 "정상적인 동시성 충돌"인지 "버그로 인한 데이터 손상"인지 판단할 근거가 코드에 없다(§5 어디에도 catch·재시도·409 매핑이 없다). 온콜은 이 문서만 보고는 대응 절차를 알 수 없고, 결국 DESIGN-003을 역참조해야 하는데 그마저 미확정 상태(line 190)다.

**플랫폼 아키텍트**: 이 문서는 "타입 소유 = 실행 책임 소유"라는 프레임을 command-application 전체에 못박는다. 문제는 이게 04번 문서 하나로 끝나지 않는다는 것 — timetable 등 다른 애그리거트도 동일 패턴(§4 `timetable/ …`)을 그대로 복제하도록 구조를 잡아놨다. 만약 이후 "경합 높은 aggregate는 비관 락"이라는 혼용 방향이 확정되면, 이 패턴을 복제한 모든 서비스가 포트 시그니처부터 다시 손봐야 한다 — 지금 정하지 않은 결정의 대가가 모듈 하나가 아니라 command-application 전체 표면적으로 곱해진다.

## 5. 핵심 취약점

미결로 미룬 동시성 결정(§7 M-2/동시성)이 이미 포트 계약에 각인돼 버렸다. `append(events)`에 버전 파라미터가 없다는 것은 "가드 없음"을 기본값으로 굳힌 것이고, 나중에 낙관 CAS나 비관 락으로 확정하면 포트 시그니처 변경 → infra 구현·테스트 동반 변경이 강제된다. "나중에 정한다"고 미뤄둔 항목이 실은 코드 형태로 이미 한 방향(가드 없음)을 선택해 버린 상태다 — 이는 숨은 가정 3개 중 사실상 전부(동일 datasource·순수 변환·무시할 리플레이 비용)가 "지금은 검증 안 됐지만 실무에선 그렇게 취급된다"로 동시에 무기화된 결과다.

## 6. 가역성

대체로 reversible(구현 전 문서). 단 `EventStorePort.append`/`load` 시그니처는 infra 구현·테스트가 한 번 붙는 순간 one-way door에 근접한다 — `expectedVersion`을 지금 넣지 않으면 이후 추가는 시그니처 변경 + 기존 append 호출부 전수 수정 + 마이그레이션이 된다.
