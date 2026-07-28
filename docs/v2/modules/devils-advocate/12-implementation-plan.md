# Devil's Advocate — 12 · Implementation Plan

> 대상: [[12-implementation-plan]]

## 1. Position + Steel-man

- **Position**: Strangler Fig로 timetable→reservation을 레퍼런스 컨텍스트 삼아 ES/커맨드-쿼리 패턴을 고정 일수(Day 1~28+) 스케줄로 검증한 뒤, 나머지 5개 컨텍스트(restaurant/schedule/user/authenticate/menu 등)에 그 패턴을 복제한다.
- **Steel-man**: 팀이 이미 ES/CQRS를 다뤄본 적 있고, 레퍼런스 컨텍스트 하나에서 M-항목·C-항목이 실제로 별 문제 없이 해소되며, 나머지 컨텍스트가 정말 "패턴 복제"만으로 충분할 만큼 단순할 때 — 이 계획은 최선이다.

## 2. 숨은 가정

1. **JIT 해소가 재작업을 유발하지 않는다** — M-1~M-9, C-1~C-7 총 16개 미결 항목이 "구현 시 확정"으로 미뤄져 있는데, 그 확정이 이미 만들어진 command-core/command-application 구조를 바꾸지 않을 것이라는 가정.
2. **패턴이 컨텍스트 간 이전 가능하다** — timetable(가장 단순한 ES)·reservation(사가 포함)에서 얻은 패턴이 schedule/user(비-ES, 상태+Outbox)에도 거의 그대로 복제된다는 가정. 7-6에는 이 두 번째 패턴에 대한 일수 배정이 아예 없다.
3. **일정이 단일 순차 크리티컬 패스로 진행되고 팀 역량·병렬성에 여유가 있다** — Day 15-22(8일)에 command-adapter+infrastructure+auth-server+Kafka+Outbox relay+Testcontainers를 한 번에 묶은 것은 병목/블로킹 없는 순조로운 진행을 전제.

## 3. 반론

### 반론 1 — [type: structural, severity: critical — 부분 해소됨(2026-07-20 동기화)]
이 반론은 C-1·C-3·C-6이 모두 미결로 "설계 반박 → 구현 시 확정" 표에 방치돼 있을 때 성립했다. 그중 **C-3(Zero Payload time-travel 오염)은 RFC-029 합의로 해소**됐고(event-carried 일원화), 함께 갱신하며 **C-4·C-5·C-7·M-9도 각각 RFC-025·RFC-030·ADR-016·RFC-020으로 해소**됐다 — 16개 미결 중 5개가 닫혔다. 그러나 **C-1(동일 datasource)·C-6(projector 쓰기 병목)은 여전히 미결**이고, 이 둘 다 command-core(7-2)·command-application(7-3) 구현 이후·이전 단계에 걸쳐 영향을 주므로 반론의 핵심 골자(검증 안 된 결정이 레퍼런스 모듈에 먼저 굳는 순서 문제)는 남아 있다.
선례: no clear precedent — speculative concern (일반적인 "미검증 스파이크가 기반 구조로 굳어지는" 패턴에 대한 유비이지 이 프로젝트의 실측 사례는 아님).

### 반론 2 — [type: timing, severity: high]
Day 1~28+ 스케줄은 병렬성·버퍼 없는 단일 순차 일정으로 읽히며, 팀이 이 스택에서 첫 ES 구현일 가능성이 높은 7-2(ES 코어, 5일)에 리스크가 가장 집중되어 있음에도 가장 먼저·가장 짧게 배정되어 있다. M-5(스냅샷 주기 "측정 후 결정")·M-6(Read DB 분리 시점)처럼 "측정 후 결정"이라 명시된 항목이 스케줄 내부에 있다는 것 자체가, 그 결정에 필요한 실측 데이터가 나오기도 전에 후속 일정(7-4, 7-5)이 이미 확정되어 있음을 의미한다. 7-2/7-5가 밀리면 7-6의 5개 컨텍스트 반복 전체에 지연이 배수로 전이된다.
선례: no clear precedent — speculative concern.

### 반론 3 — [type: assumption, severity: medium]
"레퍼런스 컨텍스트" 정의(timetable→reservation)는 전부 ES 패턴이고, 7-6에서 명시적으로 "비-ES 레퍼런스"라 부르는 schedule은 정작 7-0~7-5(약 23일)의 투자 대상이 아니다. 즉 23일 분량의 학습·검증은 ES 컨텍스트(restaurant)에만 직접 재사용되고, schedule/user가 필요로 하는 상태+Outbox 패턴은 별도의, 스케줄에 일수조차 배정되지 않은 두 번째 학습 곡선이다. "패턴 복제"라는 표현이 이 차이를 가린다.
선례: no clear precedent — speculative concern.

## 4. 페르소나 공격 — CFO / 일정 지연 비용

"Day 23-28" 같은 표기는 확정된 캘린더처럼 읽히지만, 그 이전 단계(M-6 Read DB 분리 시점, C-6 프로젝터 스케일)가 미결인 채로 시작된다. 이해관계자가 이 문서만 보면 일정에 내재된 불확실성(16개 미결 항목)을 인지하지 못한 채 "Day 28 = 쿼리 단계 완료"로 받아들일 위험이 있다. 미결 항목표(§2, §3)가 일정표(§1) 안에 게이트로 명시되어 있지 않아, 지연이 발생해도 어느 미결 항목 때문인지 사후에야 드러난다.

## 5. 핵심 취약점

**갱신**: 16개 중 5개(C-3·C-4·C-5·C-7·M-9)는 해소됐지만, 순서 문제 자체는 남아 있다. **남은 미결(C-1 동일 datasource, C-6 쓰기 병목, C-2 다중소스 원자성 + M-1~M-8)을 해소하기 전에 "레퍼런스로 복제될" 핵심 모듈을 먼저 구현하고, 그 패턴을 5개 컨텍스트에 반복 적용하는 순서** 는 여전히 스케줄에 게이트가 없다. 이는 §2의 숨은 가정 1("JIT 해소가 재작업을 유발하지 않는다")이 무기화된 형태다.

## 6. 가역성

컨텍스트 단위로는 Strangler Fig 특성상 개별 BC 전환은 reversible(롤백 가능)하지만, "레퍼런스 패턴을 5개 컨텍스트에 복제"하는 전략 자체는 복제가 시작된 뒤에는 사실상 one-way door — C-1/C-3/C-6이 늦게 뒤집히면 고침 비용이 이미 복제한 컨텍스트 수만큼 배가된다.
