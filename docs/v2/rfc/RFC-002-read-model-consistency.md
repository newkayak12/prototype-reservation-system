# RFC-002 — 읽기 모델·일관성

- **상태**: Open · 논의 중 · 2026-06-15
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-INDEX]]
- **닫으면**: [[03-read-model]] 보강 + [[04.read-model-projection-and-replica]] 개정/비준 (필요 시 신규 ADR)

## 맥락

[[RFC-001-v2-cqrs-and-event-sourcing]]에서 V2의 읽기 전략은 한 문장으로 잠갔다 — query는 query DB의 projection만 읽고, replica는 HA 목적일 뿐 읽기 라우팅에 끼지 않는다. 전략은 깔끔하지만, 그 한 문장은 "무엇을 어떻게 읽기 모델에 둘지"라는 실제 질문을 거의 건드리지 않은 채 남겨 둔다.

긴장은 여기서 나온다. command 측은 ES로 가고 이벤트가 비동기로 흘러 projection을 갱신한다. 그러면 읽기 모델은 본질적으로 *뒤처진다*. 이 지연을 정상으로 받아들이면 "쓰고 바로 읽었더니 없다"가 기본 사양이 되고, 받아들이지 않으면 동기 프로젝션이나 command DB 직접 읽기 같은 예외를 열어 CQRS 분리를 군데군데 무너뜨려야 한다. 동시에 모든 컨텍스트가 이벤트를 흘리는 건 아니다 — 자주 안 변하는 lookup 데이터(category, company, menu)나 아예 ES로 가지 않는 비-ES 컨텍스트가 있고, 이들을 "projection만 읽는다"는 규칙에 어떻게 끼워 맞출지가 애매하다. 이 RFC는 그 애매함을 따라가며 방향을 잡는다. 출처는 주로 [[03-read-model]] · [[04.read-model-projection-and-replica]] · [[13.db-hosting-and-read-write-topology]]다.

## 논의

### 저빈도 lookup을 어떻게 읽기 모델에 실현하나

category·company·menu처럼 거의 안 변하는 참조 데이터부터 보자. 선택지는 셋이다 — (a) query DB에 경량 projection 테이블을 만들어 두기, (b) 해당 lookup을 소유한 컨텍스트가 published한 테이블을 구독해 채우기, (c) 배포 시점에 적재되는 seed 참조 데이터로 두기.

하나로 통일하고 싶은 유혹이 있지만, 변화 빈도와 소유 컨텍스트가 항목마다 다르기 때문에 단일 정답이 없다. 거의 불변이고 배포 단위로만 바뀌는 것은 (c) seed가 가장 싸다 — 이벤트 파이프라인을 태울 이유가 없다. 소유 컨텍스트가 명확히 있고 그쪽이 변경을 발행한다면 (b) 구독이 자연스럽다. 그 외 읽기 요구는 있는데 발행원이 마땅찮은 것만 (a) 경량 projection으로 둔다. 즉 내 입장은 *컨텍스트·항목별로 (a)/(b)/(c)를 귀속*시키는 것이지 하나로 강제하지 않는 것이다. (이의 여지: company/menu의 실제 변경 빈도와 소유권이 불명확하면 귀속이 흔들린다 — 표 확정은 [[03-read-model]]에서.)

### read-your-writes를 어디까지 인정하나

비동기 projection의 직접적 귀결은 "방금 쓴 걸 바로 읽으면 아직 없을 수 있다"는 것이다. 이걸 버그가 아니라 *기본 사양*으로 못박는 게 출발점이라고 본다. 그러지 않으면 일관성 예외가 시스템 전체로 번져 CQRS의 이점이 사라진다.

예외를 여는 수단으로는 (b) 특정 화면에 한정한 동기 프로젝션, (c) 버전 토큰으로 클라이언트가 자기 쓰기 반영을 기다리는 read-your-writes, (d) 특정 read만 command DB를 직접 읽는 정적 바인딩 예외가 있다. 셋 다 분리를 깨는 비용이 있으므로 기본값으로 깔면 안 된다. 내 입장은 *정책*을 먼저 정하는 것이다 — 기본은 최종 일관성, 예외는 "이 화면이 즉시 반영을 요구한다"가 증명된 경우에만 승인. 어떤 수단((b)/(c)/(d))을 쓸지는 그 화면의 성격을 보고 그때 고른다. 지금 화면 목록을 미리 못박지 않는 이유는, 증거 없이 예외를 여는 게 바로 우리가 막으려는 것이기 때문이다. (이의 여지: 예약 확정 직후 내 예약 목록처럼 명백히 즉시 반영이 필요한 화면이 이미 있다면 그건 RFC 단계에서 예외로 인정해도 된다 — 후보가 나오면 여기에 적는다.)

### 프로젝션 지연을 얼마나 허용하나

지연 자체를 정상으로 받아들이기로 했으니, 남는 건 "얼마까지"다. 절대 숫자(p99 몇 ms)는 지금 정할 수 없다 — 실제 메시징 lag을 측정하기 전엔 근거 없는 숫자가 된다. 그래서 여기서는 *측정 트리거*와 정책 형태만 정한다: p99 지연 목표를 두고 초과 시 알람을 건다는 골격은 지금, 그 목표의 절대값은 [[RFC-003-messaging-delivery]]의 lag 측정과 함께 운영 단계에서 튜닝한다. 방향은 여기서, 숫자는 거기서.

### 컨텍스트별 초기 읽기 전략과 프로젝션 적용 범위

[[03-read-model]]에는 컨텍스트별 초기 읽기 전략 표가 "초안" 상태로 들어 있고, 특히 schedule이 "변화 빈도 보고 결정"이라며 프로젝션이냐 경량 lookup이냐를 미뤄 둔 채다. 이 RFC에서 그 표를 컨텍스트별로 확정 방향을 잡는다 — 다만 확정의 *원칙*은 분명하다: "실제 읽기 요구가 있는 곳부터" projection을 만든다(YAGNI). 모든 컨텍스트에 선제적으로 projection을 깔지 않는다.

따라서 1차 전환에서 실제로 projection을 만들 컨텍스트는 읽기 요구가 입증된 것으로 한정한 목록으로 못박는다. schedule처럼 빈도 판단이 필요한 항목은 그 빈도가 높고 읽기 요구가 분명하면 projection, 그렇지 않으면 경량 lookup으로 귀속시키되, 이 귀속의 실제 표는 [[03-read-model]]에서 확정한다. (이의 여지: "읽기 요구가 입증됐다"의 기준이 느슨하면 결국 다 projection이 된다 — 기준을 표와 함께 명시.)

### 비-ES 컨텍스트는 projection으로 통일하나

[[03-open-decisions]] Decision C-4가 남긴 질문이다. ES로 가지 않는 컨텍스트도 query DB projection으로 읽기를 통일할지, 아니면 기존 QueryDSL 조회를 그대로 둘지. 통일은 모델이 깔끔해지지만, 발생시킬 이벤트도 없는 컨텍스트에 projection 파이프라인을 억지로 얹는 건 비용 대비 이득이 의심스럽다. 내 입장은 비-ES 컨텍스트는 기존 QueryDSL 조회를 유지하는 쪽이다 — "query는 projection만 읽는다"는 규칙은 ES로 전환된 컨텍스트에 적용되는 규칙이지, 시스템 전체를 강제로 ES화하라는 요구가 아니다. (이건 Design에서 검증: 비-ES 컨텍스트가 ES 컨텍스트의 데이터를 조인해 읽어야 하는 경우가 있으면 통일 압력이 생긴다.)

### query 측 layered 세부 규약

마지막은 [[03.command-hexagonal-query-layered]]가 깐 구조의 세부다. command 측은 hexagonal, query 측은 layered(web/service/repository/projection/model)로 가는데, 두 가지가 미정이다 — 레이어 간 트랜잭션 경계, 그리고 projection과 service의 책임 분리. 방향은 잡을 수 있다: projection은 이벤트를 받아 읽기 모델을 *갱신*하는 쓰기 경로, service는 그 모델을 *조회*하는 읽기 경로로 책임을 가른다. 트랜잭션 경계는 조회 경로가 단순 읽기인 만큼 service에서 닫고, projection 갱신은 메시징 소비 단위에 맞춰 별도로 닫는 게 자연스럽다. 다만 이 레이어 규약의 구체는 코드 구조에 직접 닿으므로 design_doc에서 확정한다.

## Design으로 넘기는 것

- lookup 항목별 (a)/(b)/(c) 귀속 **표** 확정 — [[03-read-model]].
- read-your-writes 예외 화면 후보 검증과 수단((b)/(c)/(d)) 선택 — 증거가 나오는 화면별로, 필요 시 신규 ADR("읽기 신선도 예외 정책").
- 프로젝션 지연 p99 목표 절대값 — [[RFC-003-messaging-delivery]] lag 측정 후 운영 튜닝.
- 1차 projection 대상 컨텍스트 목록과 "읽기 요구 입증" 기준 — [[03-read-model]].
- 비-ES 컨텍스트의 ES 데이터 조인 필요성 검증 — Design.
- query layered 트랜잭션 경계·projection/service 책임 분리 구체 — design_doc.

여기서 정한 방향: 컨텍스트·항목별 lookup 귀속, 기본=최종 일관성·예외는 증명된 화면만, 지연은 측정 트리거 정책, projection은 읽기 요구 입증된 곳부터(YAGNI), 비-ES는 QueryDSL 유지, layered는 projection=갱신/service=조회로 책임 분리.

## 관련 문서

- [[RFC-INDEX]] · [[03-read-model]] · [[04.read-model-projection-and-replica]] · [[13.db-hosting-and-read-write-topology]] · [[03.command-hexagonal-query-layered]] · [[03-open-decisions]]
