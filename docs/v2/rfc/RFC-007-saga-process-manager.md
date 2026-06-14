# RFC-007 — Saga·프로세스 매니저

- **상태**: Open · 2026-06-14
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-002-decision-queue]]
- **닫으면**: [[06-consistency-and-sagas]] 보강 + [[08.saga-orchestration-vs-choreography]] 비준 (Proposed→Accepted)

## 배경

컨텍스트 간 조율은 *단순 반응 = 안무(choreography)*, *다단계 라이프사이클 = 오케스트레이션(프로세스 매니저)* 혼합으로 라운드1이 **방향만** 잡았다. 무엇을 어느 쪽으로 분류하는지, 프로세스 매니저의 상태·타임아웃·실패 처리는 거의 안 정했다 — PM 상태 모델, 타임아웃 임계, 실패의 운영 흐름 편입, 흐름 분류가 미결. 여기서 닫는다.

## 논의 항목

### Q1. 프로세스 매니저 상태 모델·영속화 위치
- **출처**: [[08.saga-orchestration-vs-choreography]] · [[06-consistency-and-sagas]]
- **옵션**: (a) 단계 enum 컬럼 / (b) ES 이벤트로 기록(`reservation`이 ES이므로 가능) — 영속화 위치는 라이프사이클 주인 컨텍스트(`reservation`)의 `command-module` 안.
- **쟁점**: PM 상태를 별도 상태 머신으로 둘지, 사가 자체를 이벤트 스트림으로 재구성할지. (a)/(b) 귀속을 정한다.

### Q2. 사가 스텝 타임아웃 임계·임시 점유 TTL 값
- **출처**: [[08.saga-orchestration-vs-choreography]] · [[06-consistency-and-sagas]]
- **측정 트리거**: 정책·메커니즘은 지금 = **폴링 기반 만료**(스케줄러가 기한 지난 미완 사가/점유를 깨움, 전용 타이머 인프라 없음). 구체 분(分) 값은 화면/UX 요구에 묶이므로 구현 사이클에서 확정.

### Q3. 사가 단계 실패의 PoisonMessage 운영 흐름 편입
- **출처**: [[08.saga-orchestration-vs-choreography]]
- **쟁점**: 사가 단계 실패도 v1 [[07.reservation]](Outbox·Zero Payload·PoisonMessage·스케줄러 재처리)의 같은 운영 흐름에 태운다. 계승 범위(저장 위치·상태 추적·수동 재처리·알림)를 확정.

### Q4. 🌱 예약 외 흐름(취소·노쇼·환불 등) 조율 분류
- **출처**: [[08.saga-orchestration-vs-choreography]]
- **쟁점**: 보상·타임아웃·다단계 유무로 안무 vs 오케스트레이션 귀속을 정한다. 현재 적용 표는 **"초안"** — 🌱 이벤트 스토밍 재실시 후 카탈로그와 함께 확정.

## 닫는 방식

- Q1·Q3 = **논의로 지금 결정**.
- Q2 = **측정 트리거**(정책·메커니즘 지금, 숫자 운영시).
- Q4 = 🌱 **이벤트 스토밍 의존** — 카탈로그 확정 후 분류 표 비준.

## 산출물

- [[06-consistency-and-sagas]] §사가 타임아웃/만료 보강, PM 상태 모델 명시.
- [[08.saga-orchestration-vs-choreography]] §적용(초안) 표 → 확정 표, TBD 해소 → `Accepted` 승급.
- 필요 시 신규 ADR(예: "프로세스 매니저 상태 모델").

## 관련 문서
- [[RFC-002-decision-queue]] · [[06-consistency-and-sagas]] · [[08.saga-orchestration-vs-choreography]] · [[07.reservation]]
