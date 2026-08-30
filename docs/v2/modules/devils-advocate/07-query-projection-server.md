# Devil's Advocate — 07 · query — Projection 서버

> 대상: [[../07-query-projection-server]]

## 1. Position 명료화 + Steel-man

**Position (한 줄)**: read model은 이벤트에서 파생된 버릴 수 있는 2차 구조물이니, Parallel Consumer(KEY 순서) + inbox 멱등 + blue-green 재구축이면 at-least-once를 effectively-once로 흡수하며 안전하게 쓰기 경로를 운영할 수 있다.

**Steel-man (한 줄)**: 조회 API와 컨슈머 루프를 별 Deployment로 갈라 장애를 격리하고, 정확성 불변식(per-aggregate 순서·멱등·버전 가드)을 발명하지 않고 재사용하며, 틀리면 통째로 다시 만들 수 있게 설계한 것은 CQRS/ES 읽기 모델 운영의 정석이다. 재구축이 항상 가능하다는 전제만 성립하면 나머지 리스크는 "속도" 문제로 격하되고 "정합성 붕괴" 문제가 되지 않는다.

## 2. 숨은 가정

1. **inbox는 `event_id` dedup만으로 충분하다** (§5.2 예시가 `inbox.exists(eventId)`만 본다). "중복은 흡수, 순서는 Kafka 파티션이 지켜준다"를 전제한다 — 순서 역전을 감지·교정할 별도 장치는 없다는 뜻.
2. **projector의 스케일 축 = concurrency 노브 / 파티션 수**(§5.1, §8.1). DB upsert 자체가 병목이 아니라는 가정 — read-modify-write형 버전 가드가 도입돼도 이 가정이 유지된다고 본다.
3. **부분 갱신(다중 소스 순서 역전)은 "정상 동작"으로 받아들일 수 있다**(§6). 최종 일관성 지연이 비즈니스적으로 언제나 허용된다는 가정 — 화면이 ES 여부와 무관하게 여러 컨텍스트를 동시에 보여줘야 하는 순간은 검증되지 않았다.

## 3. 반론

### 반론 1 — 이 문서는 자기 의존 문서(RFC-025)에 이미 supersede된 결정을 그대로 싣고 있다.

`[type: structural]` · **severity: critical**

Steel-man: 재시도→DLQ→수동 재생(§8.2)과 competing-consumers/SKIP LOCKED(§5.1)는 표준 메시징 운영 패턴이다.

이 문서 한정 비판: [[RFC-025]]는 상태 **🏷 합의(2026-07-04)**로, (a) SKIP LOCKED 경쟁 relay를 **단일 순차 relay(ShedLock)**로 supersede하고, (b) **DLQ는 라이브 스트림에 절대 되쏘지 않음 — 복구는 event_store 재구축**으로 수동 재생을 supersede했다. 그런데 §8.2는 여전히 "기본 수동 재생"을, §5.1은 여전히 "competing consumers"를 규범으로 적는다. P-4는 이걸 "RFC-025에서 확정"이라며 미결로 미루지만 RFC-025는 이미 확정됐다 — 문서가 자기 의존성보다 뒤처져 있다. 구현자가 이 문서만 보면 이미 폐기된 복구 절차를 코딩한다.

선례: 상류 결정과 하류 런북이 어긋난 채 배포되어 on-call이 폐기된 절차를 따라 사고를 키운 사례는 흔하다 — Knight Capital(구·신 코드 경로 혼재로 45분 만에 $440M 손실).

### 반론 2 — 이 projector의 inbox 스키마로는 문서가 약속한 정확성 불변식을 구현할 수 없다.

`[type: structural]` · **severity: high**

Steel-man: §7이 "정확성 불변식 재사용(per-aggregate 순서 + 멱등 upsert + `sequence_no` 버전 가드)"을 명시했으니 순서 역전은 가드가 잡는다.

이 문서 한정 비판: 그 가드의 물리적 토대를 RFC-025 결정 5가 못박았다 — "inbox에 **aggregate별 last-applied `sequence_no`** 추가". 그런데 §5.2의 inbox는 `event_id`만 기록한다("봤나?"만 봄). sequence_no가 없으면 Last-Writer-Wins 가드도, 갭 감지도 불가능하다. §7이 "재사용한다"고 선언한 불변식은 §5.2가 정의한 자료구조 위에서 **성립하지 않는다**. §9 할 일 목록에도 "inbox 테이블 + 멱등 기록/GC"만 있고 seq 칼럼이 없다 — 문서가 스스로 모순된다.

선례: dedup만 하고 순서 갭을 못 보는 컨슈머가 재정렬 하에 조용히 오염되는 것은 at-least-once 프로젝션의 전형적 실패다(RFC-025 §59가 직접 자인).

### 반론 3 — "병렬 상한 = 파티션 수"와 "Parallel Consumer로 파티션 한계 돌파"는 같은 문서 안에서 충돌하고, 진짜 상한(DB 쓰기)은 아무도 재지 않았다.

`[type: assumption]` · **severity: high — 표현 충돌은 해소됨(2026-07-20), DB 쓰기 상한 미측정은 미해소**

Steel-man: 파티션 증설로 competing consumers를 늘리면 lag이 준다(§8.1).

갱신: §5.1에 "인스턴스 내부 동시성(max-concurrency, 파티션 수 무관)"과 "인스턴스 간 수평 확장(competing consumers, 파티션 수 상한)"이 서로 다른 축임을 명시해, 겉보기 모순은 해소했다 — §3의 200 msg/s는 전자, "상한=파티션"은 후자다. 그러나 무트래픽 프로토타입에서 실제 상한이 파티션도 concurrency도 아니라 **read model DB upsert**라는 지적은 그대로 유효하다 — RFC-025의 LWW seq 가드는 aggregate 행에 대한 read-modify-write(버전 비교)라 핫 애그리거트(예: 식당 리네임이 수천 예약 행에 팬아웃)에서 **행 잠금 경합**을 만든다. concurrency를 4→8→16으로 올릴수록 경합은 악화될 수 있다. 문서는 이 상한을 한 번도 측정하지 않는다(P-5는 "레플리카로 안 풀린다"까지만 말하고 쓰기 상한 자체는 미측정) — [[12-implementation-plan]] C-6과 동일 사안, k6 실측 필요.

선례: 컨슈머 병렬도를 올려도 downstream DB 쓰기에서 막혀 lag이 안 줄고 오히려 락 경합으로 악화되는 것은 CDC/프로젝션 파이프라인의 흔한 벽이다.

## 4. 다중 페르소나 공격

**On-call / SRE — 새벽 3시.** `reservation.reservation` lag이 임계치를 넘겨 페이지가 뜬다. 런북대로 projector 인스턴스를 4→6으로 스케일아웃한다 — 파티션이 4개라 5·6번째 인스턴스는 그냥 놀고(idle) lag은 안 줄어든다(§5.1의 "상한=파티션"이 물어버린 함정). 진짜 원인은 한 인기 식당의 `RestaurantRenamed`가 수천 예약 행에 팬아웃하며 LWW 버전 가드가 행 잠금을 붙잡는 DB 경합인데, 대시보드엔 그 지표가 없다(lag만 있음, §8.1). 동시에 DLQ Slack 알람이 울리고 이 문서 §8.2 런북은 "수동 재생"을 지시한다. 그대로 seq 5를 되쏘지만 6·7은 이미 처리됐고, 순서 역전을 잡아줄 LWW 가드는 이 projector에 구현돼 있지 않다(반론 2) — 한 예약이 취소→확정으로 뒤집힌 채 굳는다. 최후 수단인 blue-green 재구축을 걸지만 event_store 전체 리플레이는 진행률 표시도 fencing도 없어(§7 "원자 스왑"은 한 줄), 몇 시간짜리 리플레이가 끝날 때까지 green이 blue의 마지막 오프셋을 실제로 따라잡았는지 확인할 길이 없다.

**주니어 — 입사 첫날.** §5.2의 깔끔한 inbox 예시를 그대로 베껴 `ReservationListProjector`를 짠다 — `event_id`만 기록. RFC-025가 요구하는 aggregate `sequence_no`는 이 문서 어디에도 코드로 없으니 넣을 생각을 못 한다. §3.1의 "복합 키 `timeTableId_timeTableOccupancyId`"를 Parallel Consumer의 ordering 키로 쓴다 — 하지만 §5 mermaid와 RFC-021의 파티션 키는 `aggregate_id`다. 순서 단위가 갈린다: 같은 aggregate의 두 이벤트가 서로 다른 KEY 레인으로 흩어져 부하 상황에서 재정렬된다. 로컬 단일 스레드 E2E(§10)는 통과한다 — 재정렬은 동시성이 있어야 드러나므로. 프로덕션 concurrency=16에서만 조용히 깨진다.

## 5. 핵심 취약점 (하나)

**갱신(2026-07-20)**: inbox `event_id`-only 문제(반론 1·2)와 스케일 모델 표현 충돌(반론 3 전반부)은 모두 해소됐다. **남은 핵심 취약점은 read model DB upsert의 실제 쓰기 상한이 미측정이라는 점**(반론 3 후반부) — LWW seq 가드의 read-modify-write가 핫 애그리거트에서 행 잠금 경합을 만들 수 있는데, 이 상한을 재는 계획이 문서에 없다.

## 6. 가역성 프레임

**혼합 — read model 값 오류는 reversible(재구축이 이 설계의 최강 카드), 그러나 inbox 스키마 + 파티션 키 계약은 one-way door.** 첫 레퍼런스 projector가 `event_id`-only inbox와 복합 키를 굳히고 나면 나머지 projector들이 그 패턴을 복제하고 Flyway 마이그레이션·토픽 키가 그 위에 쌓여, 되돌리려면 전 projector 재작성 + 토픽 재키잉이 필요해진다. 지금(첫 레퍼런스 착수 전, Phase 7-5)이 그 문을 닫기 전 마지막 지점이다.
