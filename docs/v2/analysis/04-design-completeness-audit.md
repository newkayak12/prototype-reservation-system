# V2 설계 완전성 전수 감사 (Design Completeness Audit)

> 생성: 2026-06-16 · 브랜치: `docs/event-sourcing`
> 방법: RFC 20 + ADR 15 + design_doc 16을 6개 클러스터로 나눠 병렬 감사. 각 클러스터가 동일 루브릭(**결정됨 / 명시적 보류 / 빠진 결정·모순**)으로 보고.
> 목적: 구현 사이클 진입 전 "정말 다 정했는가" 판정 + 미결 추적.

## 판정 요약

| 클러스터 | 판정 | 가장 load-bearing 미결 |
|---|---|---|
| 이벤트스토어·쓰기모델·스키마진화·동시성 | **N** | 시퀀스 채번 chokepoint·lock-wait/409타이밍 (① `event_id`(UUIDv7)·동시성 비관전환은 닫힘 — `global_seq`는 [[RFC-021-event-identity-and-global-ordering]] 닫힘으로 불채택) |
| 메시징·읽기모델·재구축 | **N** | consumer checkpoint 저장위치·projector별 inbox/guard 결정표·교차 애그리거트 순서 |
| 사가·일관성·결제 | **N** | 크래시 재개·stuck 사가 조회·paid-after-expiry·3-event 계약 |
| API·인가·캐싱·토큰 | **N** | 202 결과조회·비동기 command 인가 신선도·revocation blast-radius |
| 마이그레이션·모듈·데이터이행 | **N** | 첫 slice 미잠금·one-way sync 미설계·genesis PII·Flyway 듀얼DS 소유 |
| PII·관측성·테스트·DR·배포 | **N** | key-store DR 역설·Kafka/프로젝션 삭제 커버리지·마이그레이션 실행 주체·리플레이 결정성 |

**전체: 6/6 N (구현 진입 불가).** 원칙은 잘 잠겼으나 "정확성을 결정하는 한 줄"이 일관되게 impl 사이클로 미뤄짐.

## 판별 원칙 (검사 대상 구분)

- **숫자 보류 = 괜찮음** (검사 대상 아님): 파티션 수·retention·TTL·커버리지 임계·RPO/RTO·lag 임계. 트래픽 없음 → 측정 후 결정이 맞음 (learning 우선).
- **정확성 규칙 보류 = 미결** (아래 추적): handler/projector/saga 코드를 짜려면 규칙을 *발명*해야 하는데 없음.
- 판별식: **"이 결정 없이 command handler / projector / saga를 작성할 수 있나?"** → 못 쓰면 빠진 결정.

---

## 선행 게이트 (이게 안 끝나면 절반은 못 닫음)

- [ ] **G0 — 도메인 이벤트 카탈로그 확정 (이벤트 스토밍 재실시)**
  - 인덱스가 스스로 "선행 작업, TBD"로 표기. 종속 미결: 3-event 계약, genesis 필드 매핑, projector별 inbox/guard 표, 애그리거트 입도(hot slot), per-context terminal-state 목록.
  - **이게 토대.** 카탈로그 없이 ①③⑦의 절반은 정의 불가.

---

## 횡단 load-bearing 결정 7개 (어느 단일 문서도 소유 안 함)

> 괄호 = 독립적으로 같은 구멍을 지적한 클러스터 수(신호 강도). 이게 전수 검사의 핵심 산출물.

- [x] **① 글로벌 순서 & 이벤트 정체성 — 닫힘 (2026-06-17 · [[RFC-021-event-identity-and-global-ordering]] → [[22.event-identity-and-global-ordering]])**
  - 결정: `event_id`(UUIDv7, 전 컨텍스트 공통 정체성 = inbox/dedup·causation 앵커 + 재구축 *keyset* 열거 커서 겸용, 교차 전순서 아님) 추가. `(aggregate_id, sequence_no)` UNIQUE 유지. (전용 `global_seq`는 [[RFC-021-event-identity-and-global-ordering]] 닫힘으로 불채택 — UUIDv7이 커서를 겸한다.)
  - 핵심 reframe: "순서"는 요구가 아니었다 — 재구축은 *열거/재개*만 필요. 프로젝터 정확성 = per-aggregate 순서+멱등+버전 가드, 교차 순서는 사가([[09.event-ordering-and-delivery-guarantee]]).
  - `adr/05`는 합의됨(immutable)이라 제자리 수정 대신 [[22.event-identity-and-global-ordering]]가 스키마 보강. dd02/dd08/RFC-012 동반 반영 완료.

- [x] **🔄 동시성 전환 — 낙관 → 비관 락 (2026-06-17 · [[RFC-014-aggregate-concurrency-control]] §재개 → [[16.optimistic-concurrency-control]] 재작성)**
  - ① 처리 중 동시성 재검토 + Redisson 활용 결정. **L0 UNIQUE(안전·불변) + L1 Redisson 분산 락(1차) + L1′ DB 비관 락(Redis 다운 폴백, 낙관 회귀 없음).** 락=liveness, UNIQUE=safety. 전역 락 금지·교차는 사가.
  - adr/19 §동시성 일원화 문구 정합 수정. 잔여(클러스터 A의 채번 chokepoint·lock-wait/409 타이밍)는 비관 모델로 **재맥락화**되어 구현 사이클로 — 아래 A 참조.

- [ ] **② read-your-writes + 202 결과 조회 — 가장 많이 참조된 미결 (4)**
  - 202 비동기 계약 전체가 종속. RFC-003은 "화면이 증명되면"으로 미룸.
  - 파생: 클라가 command 성공 인지 불가(poll/SSE/ws 미정) / 422·409가 동기응답인지 결과조회 시점인지 미정(에러 계약 두 모양) / ownership 변경 stale 권한 창.
  - **마감 액션:** 신규 ADR — 결과조회 메커니즘 + 동기-거부 vs 202 경계 + 락-409 노출 시점 + (필요시) version-token 포맷/transport.

- [ ] **③ PII 파이프라인 전 구간 누락 (3)**
  - 크립토 셰딩(`adr/11`)은 정상상태만 설계. 누락: **genesis import**(V1 스냅샷이 append-only에 PII 박음, `rfc014`에 PII 단어 없음) / **Kafka 토픽**(`_encrypted` 봉투 vs 평문 미명시, retention↔삭제 충돌 미직면) / **프로젝션·Redis**(복호화 PII 적재 여부·삭제 시 재구체화 미정).
  - **마감 액션:** RFC-006 확장 — import/topic/projection 각 구간의 PII 처리 invariant 명시. `rfc014`는 ADR-11 cross-ref 필수.

- [ ] **④ key-store DR 복구 역설 — 모순 (PII)**
  - "DR 위해 키 백업" vs "셰딩 위해 키 백업에서 삭제/제외" 정면 충돌. key_store 분실 = 전 PII 영구 복구불가. 복구 경로를 RFC-018↔RFC-006이 서로 미룸.
  - **마감 액션:** 소유권 확정 — 키 백업 + 복원 시 erasure 로그 재적용 같은 reconciliation 설계, 또는 명시적 trade-off 수용 기록.

- [ ] **⑤ Flyway / 마이그레이션 실행 주체 — 무주공산 (3)**
  - command DB + query DB 물리분리 + 도메인별 스키마인데 DDL 소유·배포 시 실행 워크로드·projector 롤아웃 순서 미정. (CLAUDE.md상 Flyway는 adapter-module 단일 DS)
  - **마감 액션:** 신규 결정 — 듀얼 DS Flyway 소유 + 배포 시 마이그레이션 잡 + 스키마 vs projector 롤아웃 순서.

- [ ] **⑥ strangler 혼재 창의 컨텍스트 간 ACL (migration)**
  - "Zero-Payload라 안전"만 단언. V2 컨텍스트가 V1 컨텍스트 이벤트 소비 시 old↔new 스키마 anti-corruption 매핑 hand-wave. + `adr/13`("모든 query 타깃=projection") ↔ "5단계 V1 유지" 모순.
  - **마감 액션:** `adr/06`/`dd04`/contract — 혼재 창 ACL 매핑 명시 + adr/13 모순 해소.

- [ ] **⑦ 사가 크래시 복구 + stuck 관측 + poison 런북 (2)**
  - PM rehydrate/resume 미정 / 열린 사가 deadline 조회 read model 없음 / 부분 보상 추적 불가(RFC-007 자인) / poison 재주입 런북 부재.
  - **paid-after-expiry 레이스:** 결제 확정이 좌석 해제 후 도착 → 돈만 받고 좌석 없음, 보상 경로 미연결.
  - **마감 액션:** RFC-007/dd06 — PM 복구·open-saga 프로젝션·보상 진행상태 추적·paid-after-expiry 보상 트리거.

---

## 클러스터별 상세 미결 (디테일 보존)

### A. 이벤트스토어·쓰기모델 (판정 N)
- [ ] 재시도 예산/backoff·409 escalation 타이밍 미정 — **비관 전환으로 재맥락화**: lock-wait 타임아웃→409/503, 도메인 거절→422/409 확정적, 잔여 UNIQUE 충돌→바운디드 재시도([[16.optimistic-concurrency-control]]). 구체 수치·202 노출 시점은 구현 사이클(RFC-013과)
- [ ] 시퀀스 채번 chokepoint 요구만 하고 미설계 (read-max↔insert 레이스) — 비관 락이 채번 경합을 직렬화하나 채번 단일화·`DuplicateKey`→충돌 매핑은 여전히 구현 과제([[16.optimistic-concurrency-control]] 미결)
- [ ] DuplicateKey 구분 불가 — 진짜 버그를 conflict로 오인해 무한 retry/오흡수 (silent 무결성 구멍)
- [ ] 이벤트 직렬화 계약 미정 (null/enum/time/money/unknown-field) — 업캐스터 파싱 검증 불가
- [ ] 업캐스트 런타임 실패 모드 미정 (malformed/upcaster 부재 시 reject/halt/poison?)
- [ ] 스냅샷 read-path 부분손상 (version 일치하나 deserialize 오류)은 sampled 배치만 잡음 — live command에 오상태
- [ ] 리플레이 비용 상한 없음 (hot aggregate가 스냅샷 cadence 추월 시 retry storm)
- [ ] 다중 애그리거트 command 거부 "원칙"만 — 강제 메커니즘 없음
- [x] `event_id` 부재 ↔ 멱등 컨슈머 정체성 요구 — **닫힘** ([[22.event-identity-and-global-ordering]]: event_id 1급 컬럼, inbox dedup 키)
- [ ] ES 경로 Outbox-append 원자성 실패 순서 미명시 (append 성공·Outbox insert 실패 / publish 누락 케이스)

### B. 메시징·읽기모델 (판정 N)
- [ ] consumer offset/checkpoint 저장 위치 미명 (`__consumer_offsets` vs query DB checkpoint 행) — at-least-once 중복 창 정확성 좌우
- [ ] projector별 inbox/guard/upsert 결정표 부재 ("inversion-free AND naturally-idempotent면 skip" 조건만, 분류는 보류)
- [x] 교차 애그리거트 프로젝션 순서 규칙 없음 — **닫힘(reframe)**: 교차 순서는 정확성 비의존(멱등+per-aggregate 버전 가드가 흡수), 진짜 교차 순서는 사가. 재구축 열거 커서는 `event_id`(UUIDv7) keyset이지 순서 보장 아님 — 전용 `global_seq`는 불채택 ([[22.event-identity-and-global-ordering]])
- [ ] read-your-writes 미해결 + 클라 표면화 미정 (→ 횡단 ②)
- [ ] read-model 테이블 DDL 소유·마이그레이션 툴 미명 (→ 횡단 ⑤)
- [ ] blue-green swap 원자성 메커니즘 보류(alias vs app-switch) — 재구축 정확성이 여기 의존 + swap 창 도착 이벤트 미처리
- [ ] consumer-group rebalance 중 in-flight·중복 창 미처리 (배포/스케일업마다 발생)
- [ ] backpressure escalation ladder 없음 (partition 증설 = stop = 사실상 outage)
- [ ] 신규 프로젝션 런타임 삽입(blue 없는 backfill-while-live) 구체 미명
- [ ] DLQ 토픽/스키마/replay 경로 미정 — 순서 깨고 재주입 시 정확성 위험

### C. 사가·결제 (판정 N)
- [ ] PM 크래시 재개 미정 (→ 횡단 ⑦)
- [ ] stuck 사가 조회 read model 없음 (→ 횡단 ⑦)
- [ ] 사가 timeout vs seat-hold TTL 두 시계 — 누가 먼저, paid-after-expiry (→ 횡단 ⑦)
- [ ] 3-event 계약(payload·correlation key) 미정 — "frozen"인데 스키마 없음 (→ G0)
- [ ] `RequestPayment`↔결제이벤트 correlation key 미정 — 응답 라우팅 불가
- [ ] 다단계 보상 순서·원자성·보상 실패 미정 (좌석 해제됐는데 환불 안 됨)
- [ ] 부분 보상 상태 vs PoisonMessage — RFC-007 자인, 미해결
- [ ] non-idempotent PG 이중과금 fallback 미설계 (결제 경계가 학습 핵심인데 열림)
- [ ] charge/refund idem-key space 공유 미정 (이중환불/원거래 dedup 위험)
- [ ] PG timeout(불확정 결과) 상태·재시도·max-attempt 미정
- [ ] PM consume-then-emit 원자성(inbox/outbox) 미배선
- [ ] confirm 시점 hold 재검증 없음 — 만료된 hold에 confirm = overbooking
- [ ] ADR-08 application 표 "draft" + 이벤트스토밍 의존 (cancel/no-show/refund = 돈 만지는 흐름) (→ G0)

### D. API·인가·캐싱·토큰 (판정 N)
- [ ] 202 결과 discovery 미정 (→ 횡단 ②)
- [ ] 동기-거부 vs 202 경계 미정 / 락-409 노출 시점 자기모순 (→ 횡단 ②)
- [ ] invariant 없는 create의 잔여 멱등성 미해결 — 더블클릭 중복 생성 (예약 시스템 최빈 중복)
- [ ] dedup TTL/저장 미정 + Tier-1 Redis `allkeys-lru`라 창 내 eviction → 중복 재개방
- [ ] 비동기 command 인가 신선도 — edge 검증 시점 ↔ handler 실행 시점 괴리, revoke/role 변경 in-flight 통과
- [ ] role = issue-time 스냅샷, access TTL 미정 → demote/revoke blast-radius 미정의
- [ ] revocation 포기 blast-radius 미정량 (탈취 토큰 만료까지 유효, kill switch 없음)
- [ ] refresh rotation/reuse-detection 보류 → refresh 탈취 무한 재발급 탐지 불가
- [ ] cookie SameSite/path 미확정인데 그게 CSRF 수정책 ("고쳤다" 선언이 값보다 앞섬)
- [ ] projection scope-key 강제가 "미래 ArchUnit 테스트"로만 존재 — projector가 owner_id 누락 시 테넌트 누수
- [ ] ownership 변경 stale 권한 창·per-resource 정책 없음
- [ ] query-충분 vs command-재검증(민감도 분류) 없음 — 모든 read가 stale projection 신뢰
- [ ] 캐시 무효화 vs projection 갱신 레이스 미소유
- [ ] 버저닝 메커니즘 미정 (path vs header) — 첫 breaking change 경로 없음
- [ ] "owner OR role" 도메인/보안 어휘 경계 미정 — 도메인 보안 누출 anti-pattern 위험

### E. 마이그레이션·모듈·데이터이행 (판정 N)
- [ ] genesis PII 전무 (→ 횡단 ③)
- [ ] genesis가 V1 상태 정확 재현 보장 안 됨 — 필드 매핑 spec 없음, count는 맞고 state는 발산 가능
- [ ] one-way sync 메커니즘 미설계(polling vs CDC, lag, mid-window 변환) — long-parallel 기본값이 여기 의존, 첫 컨텍스트 시작 불가
- [ ] "retention 중 V1에 V2 write 반영" 미정 → 안 하면 instant rollback이 데이터 유실 (안전망 전제 미결)
- [ ] genesis 멱등/재실행 의미 없음 — 부분 실패 후 재실행 시 append-only 중복
- [ ] Flyway 듀얼 DS 소유 (→ 횡단 ⑤)
- [ ] 혼재 창 ACL (→ 횡단 ⑥)
- [ ] "step done" 기준 너무 거침 (정량 기준·equivalence gate 기록·sync-lag 임계 없음)
- [ ] 첫 slice 미잠금 — 1~5 순서 "초안", timetable=template인데 구체 산출물 목록 없음
- [ ] ArchUnit/Konsist 규칙 명명만, 미작성 (어느 툴·테스트모듈 위치) — 모듈 분리 정당화의 강제장치가 TODO
- [ ] adr/13 "모든 query=projection" ↔ step-5 V1 유지 모순 (→ 횡단 ⑥)

### F. PII·관측성·테스트·DR·배포 (판정 N)
- [ ] key-store 분실 복구 경로 RFC 루프 (→ 횡단 ④)
- [ ] 키 백업 invariant 모순 (→ 횡단 ④)
- [ ] 키 rotation 전무 — 유출 시 재암호화·버저닝·"키=삭제단위" 충돌 무대응
- [ ] Kafka 토픽 GDPR 삭제 미직면 (→ 횡단 ③)
- [ ] 프로젝션 read model GDPR 삭제 미검증 (→ 횡단 ③)
- [ ] DR "consistent point"·"never-seen tail" 운영 정의 없음 → DR 테스트/런북 작성 불가
- [ ] event-store 백업 메커니즘·복원 검증("①restore+verify"의 verify 미정의)
- [ ] Kafka 경유 trace 전파 attribute key/namespace 미명 → "필수 correlationId" 테스트 강제 불가
- [ ] stuck projection 디버그 런북 없음 (신호는 있고 절차 없음) (→ 횡단 ⑦)
- [ ] 배포 시 마이그레이션 실행 주체 (→ 횡단 ⑤)
- [ ] 4-workload + leader election 솔로-dev 운영 부담 미인지 + leader election 메커니즘 "impl에서"
- [ ] 리플레이 결정성 오라클 미정 + 셰딩(의도적 비결정)과 replay-equivalence 게이트 충돌
- [ ] 모든 임계/SLO/커버리지 = 정책만, 숫자 미설정 → 현재 정량 게이트가 아무것도 fail 못 시킴 (숫자 보류는 OK지만 인지 필요)
- [ ] blind index 재구축/누출 lifecycle 미검증

---

## 권고 진행 순서

1. **G0 이벤트 스토밍 재실시** → 도메인 이벤트 카탈로그 확정 (①③⑦의 절반 해금)
2. **정확성 잠금 라운드** — 횡단 7개를 주제별 RFC 1개씩 (RFC discipline). load-bearing 순서: ① → ② → ③ → ④⑤⑥⑦
3. **숫자는 측정 후 보류 유지** — 단 정량 게이트 부재는 인지
4. 구현 사이클은 위가 닫힌 뒤 `adr/06` 순서대로 별도 사이클

> 다시 열 때: 위 체크박스가 작업 큐. 횡단 7개가 우선, 클러스터별 상세는 해당 RFC/ADR 작성 시 참조.
