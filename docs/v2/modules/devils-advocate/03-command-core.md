# 03-command-core 악마의 변호인 (독립 검증)

> 대상: [[03-command-core]]. 이 문서 §8에 이미 ES `apply`/버저닝에 초점을 맞춘 자체 반박이 있다.
> 아래는 그와 겹치지 않는 **다른 핵심 주장** — "순수성 보장" 메커니즘(Gradle 물리적 배제 + ArchUnit/Konsist 컨텍스트 경계) — 에 대한 독립적 재검증이다.

## 1. Position + Steel-man

**Position**: command-core는 build.gradle.kts의 물리적 의존성 배제(컴파일 타임)와 ArchUnit/Konsist의 행위적 경계 강제(빌드 타임)를 결합해, 9개 이상의 서브도메인이 공존하는 단일 서브모듈 안에서도 프레임워크 의존 0과 컨텍스트 간 격리를 동시에 보장한다.

**Steel-man**: 서브도메인 개수가 적고 경계가 초기에 안정적으로 굳어, ArchUnit 규칙 수가 적은 상태로 오래 유지되며, `shared-module`이 순수 유틸/enum만 담아 프레임워크를 전혀 끌어들이지 않는 한 이 조합은 최소 비용으로 순수성을 강제하는 가장 실용적인 방법이다.

## 2. 숨은 가정

- **B1**: "물리적 배제"(§2, `build.gradle.kts`에 spring-*/jakarta.* 없음)가 곧 "전이적으로도 프레임워크 심볼 없음"을 의미한다고 전제. 실제로는 `command-core`가 직접 선언하지 않을 뿐, 허용된 유일한 의존인 `shared-module`이 무엇을 끌어오는지에 대해서는 이 문서에 아무 보장이 없다.
- **B2**: 컨텍스트 간 참조 금지(§5.2)를 ArchUnit/Konsist 규칙 "하나"로 막을 수 있다고 암묵 전제. 문서는 규칙의 형태(전역 1개 vs 도메인쌍별 N개)와 신규 도메인 추가 시 규칙 갱신 책임을 명시하지 않는다.
- **B3**: ES 애그리거트(`EventSourcingAggregate`)와 비-ES 애그리거트(`StatefulAggregate`)가 서로 다른 서브도메인에 독립적으로 배정된다고 전제하고, 두 종류가 도메인 서비스에서 상호작용해야 하는 경우(§1 "단일 애그리거트로 안 풀리는 로직")의 API 정합은 다루지 않는다.

## 3. 반론

- **R1 · `[structural]` · severity: medium — 해소됨(2026-07-20 동기화)** — 이 반론은 shared-module 자신의 순수성을 검사하는 게이트가 없을 때 성립했다. [[01-shared-module]] §1.1(positive 기준)·§3에 Konsist **R7**(shared-module도 Spring/JPA/jakarta import 0을 빌드 실패로 강제, [[RFC-031]] 규칙 카탈로그)을 추가해, command-core의 유일 허용 의존인 shared-module도 이제 게이트가 걸린다 — "컴파일 타임 강제"라는 표현이 이제 전이적 경로까지 실제로 뒷받침된다.
- **R2 · `[execution]` · severity: medium** — §5.2의 컨텍스트 간 참조 금지는 "할 일" 목록에 "ArchUnit/Konsist 컨텍스트 간 참조 금지 규칙" 한 줄로만 존재한다. `reservation, restaurant, timetable, schedule, user, authenticate, menu, category, company` 등 9개 서브도메인 기준 금지해야 할 쌍은 최대 수십 개이며, 신규 도메인이 추가될 때마다 규칙을 갱신해야 하는데 그 트리거(누가, 언제 규칙을 추가하는지)가 문서에 없다. 규칙이 최신화되지 않으면 "빌드 실패로 강제"라는 주장과 달리 새로 추가된 경로는 조용히 뚫린 채로 통과한다. *선례: no clear precedent — speculative concern.*
- **R3 · `[assumption]` · severity: low** — ES/비-ES 애그리거트 공존(§4의 `EventSourcingAggregate` / `StatefulAggregate` 분리)을 전제하면서, 도메인 서비스가 "단일 애그리거트로 안 풀리는 로직"(§1)을 다룰 때 ES 애그리거트의 `handle→events`와 비-ES 애그리거트의 상태 변경 방식이 섞이는 경우의 합성 규약이 없다. 규모가 커지기 전엔 드러나지 않는 낮은 심각도의 설계 공백이다. *선례: no clear precedent — speculative concern.*

## 4. 다중 페르소나 공격

- **신규 도메인 추가 담당자**: "10번째 서브도메인을 추가하려는데, 기존 ArchUnit 규칙이 내 도메인과 다른 8개 도메인 사이의 금지 관계를 다 커버하는지 어떻게 확인하나? 문서에 규칙 목록도, 자동 생성 스크립트도 없다 — 수동으로 빠뜨리면 빌드는 통과하고 경계는 뚫린다."
- **빌드/플랫폼 담당자(해소됨)**: "'컴파일 타임 강제(Konsist 아님, Gradle 그래프)'라고 표현했는데, shared-module의 의존성 그래프도 이제 Konsist R7로 감시된다 — command-core에만 걸려 있던 반쪽짜리 보장이 아니다."

## 5. 핵심 취약점

**갱신(2026-07-20)**: shared-module 전이 의존성 게이트 부재(R1)는 Konsist R7로 해소됐다. **남은 핵심 취약점은 R2** — 9개 이상 서브도메인의 컨텍스트 간 참조 금지 규칙이 "할 일" 한 줄로만 존재하고, 신규 도메인 추가 시 규칙을 누가·언제 갱신하는지 트리거가 없어, 규칙이 최신화되지 않으면 빌드는 통과하지만 실제로는 새 경로가 조용히 뚫린 채로 남을 수 있다.

## 6. 가역성

**Reversible.** shared-module 순수성 게이트(R1)는 이미 반영되어 낮은 비용으로 안착했다. R2(컨텍스트 간 참조 금지 규칙의 유지보수 트리거 부재)는 서브도메인이 늘어날수록 수동 누락 위험이 커지는 방식이라, 지금 자동화(도메인 목록에서 규칙 자동 생성)를 정하는 편이 나중에 규칙이 흩어진 뒤 정리하는 것보다 싸다.
