# RFC-003 — 메시징·전달 보장

- **상태**: 🏷 합의 (2026-06-21) · design [[07-messaging-topology]] 반영 · ADR [[09.event-ordering-and-delivery-guarantee]] 비준 대기
- **선행**: [[RFC-001-v2-cqrs-and-event-sourcing]] · 인덱스 [[RFC-INDEX]]
- **닫으면**: [[07-messaging-topology]] 보강 + [[09.event-ordering-and-delivery-guarantee]] 비준

## 맥락

V1은 DB를 진실 원천으로 두고 동기 호출로 묶인 모놀리식이었다. V2에서 CQRS·이벤트 소싱으로 가면서([[RFC-001-v2-cqrs-and-event-sourcing]]), write 측이 만든 사실을 read model·프로젝터·외부 연동에게 **비동기로** 전달해야 한다. 여기서 전달 의미론이 곧장 문제로 떠오른다.

분산 메시징에서 exactly-once는 끝단까지 공짜로 주어지지 않는다. 그래서 V2의 기본 골격은 at-least-once 전달 + 멱등 컨슈머로 effectively-once를 합성하는 것이다. 순서는 `aggregate_id`를 파티션 키로 써서 aggregate 단위로만 보장하고, command DB에서 Kafka까지는 Outbox relay가 잇는다. 이 *메커니즘*은 [[09.event-ordering-and-delivery-guarantee]]에서 이미 잡았다.

문제는 메커니즘만으로 운영이 굴러가지 않는다는 데 있다. at-least-once를 진짜로 성립시키려면 커밋을 언제 찍을지, 중복을 누가 흡수할지, 멱등으로 못 막는 부수효과는 어떻게 할지, relay가 둘로 떠도 한 번만 발행되는지 — 이 긴장들이 줄줄이 따라온다. 이 RFC는 그 긴장을 따라가며 **방향**을 잡는다. 절대 수치와 직렬화 규약은 Design으로 넘긴다.

## 논의

### 커밋과 중복, 그리고 멱등의 책임 소재

at-least-once의 핵심은 사실 단순하다. 컨슈머가 메시지를 *처리하기 전에* 오프셋을 커밋하면, 처리 중 죽었을 때 그 메시지는 영영 사라진다 — at-most-once가 돼버린다. 그러니 Kafka auto-commit은 끄고, **처리가 끝난 뒤 수동 커밋**한다. 그레이스풀 셧다운 때는 인플라이트 메시지를 드레인하고 내려간다(V1 T-20 셧다운 규율 계승, [[07-messaging-topology]]·[[09.event-ordering-and-delivery-guarantee]]). 이 선택은 곧 "재처리는 정상"이라는 뜻이다 — 같은 이벤트가 두 번 올 수 있다.

그러면 중복을 누가 흡수하나. 컨슈머별로 처리한 event id를 inbox에 적어두면 재처리 때 걸러낼 수 있다. 기본은 **inbox 유지**다. 다만 모든 컨슈머가 inbox를 가질 필요는 없다 — read model 갱신이 Zero Payload upsert(id 기준 덮어쓰기)처럼 *자연 멱등*인 경우, 같은 이벤트를 두 번 적용해도 결과가 같으니 inbox를 생략할 수 있다. 단 전제가 있다: 그 생략이 안전한 건 **순서 역전이 없을 때뿐**이다. 역전이 가능하면 오래된 값으로 덮어쓰는 사고가 난다. 그래서 inbox 생략은 "순서 역전 없음 + 자연 멱등 upsert"를 동시에 만족하는 컨슈머에만 허용하고, 유지하는 inbox는 보존 기간을 짧게 가져가며 GC한다. (이의 여지: 어떤 프로젝터가 실제로 순서 역전 무풍지대인지는 토폴로지에 달렸다 — Design에서 컨슈머별로 검증한다.)

### 멱등으로 못 막는 것 — 외부 부수효과

upsert는 재적용해도 같은 상태로 수렴하니 멱등이 거저 얻어진다. 문제는 그렇게 흡수되지 않는 부수효과다. 알림 발송이나 외부 결제 연동은 at-least-once 재처리에서 **두 번 발사**된다 — 알림이 두 통 가고, 결제가 두 번 일어난다.

여기엔 단일 해법이 없고 부수효과의 성격을 따라 갈래가 나뉜다. 멱등키를 키로 갖는 **디듀프 테이블**이 기본이다 — 같은 멱등키면 두 번째 시도를 무시한다. 외부 시스템 연동처럼 자기 트랜잭션에 묶을 수 없는 건 **부수효과 outbox**로 빼서 발행 자체를 한 번만 보장한다. 그래도 자동 보정이 불가능한 잔여는 수동 보정으로 남긴다. 핵심 입장은 "재처리는 정상"을 전제로 깔았으니 **모든 비-멱등 부수효과는 디듀프나 outbox로 감싸는 게 기본값**이고, 수동은 예외라는 것. (이의 여지: outbox-of-side-effects는 relay를 하나 더 늘리는 비용이 있다 — 부수효과 유형별 귀속은 Design에서.)

### Outbox relay의 단일성

command DB에서 Kafka로 잇는 relay는 가용성을 위해 여러 인스턴스로 뜬다. 그러면 같은 outbox 행을 두 인스턴스가 동시에 집어 **중복 발행**할 수 있다. 막는 길은 둘이다 — leader election으로 하나만 일하게 하거나, `SELECT … FOR UPDATE SKIP LOCKED`로 행을 잠그며 경쟁 소비하거나.

leader election은 코디네이터(주키퍼/etcd류)와 리더 교체 로직이라는 운영 짐을 새로 진다. relay가 이미 DB에 붙어 있는 마당에, **`SKIP LOCKED`로 DB가 직렬화를 대신 해주게** 하는 쪽이 가볍다 — 여러 relay 인스턴스가 서로 다른 잠기지 않은 행만 집어가니 중복 없이 경쟁 소비가 된다([[09-deployment-runtime]]). 별도 코디네이터가 필요 없다는 게 결정적이다.

### 폴링이냐 CDC냐 — 그리고 언제 넘어가나

relay가 outbox를 읽는 방식은 폴링과 CDC(Debezium) 두 갈래다. CDC는 듀얼 라이트를 없애고 지연도 줄지만, Kafka Connect 클러스터를 운영하는 성숙도 비용이 든다([[05.event-store-mysql-table]]·[[12.kafka-hosting-msk-vs-self-managed]]). 초기 트래픽에서 그 운영비를 먼저 무는 건 과투자다. 그래서 **폴링 relay로 시작**하되, CDC는 "언젠가"가 아니라 명시적 **전환 트리거**로 정의해 둔다 — 폴링 지연이 SLI를 위협하거나, 듀얼 라이트 제거가 정합성 요구로 올라오거나, Connect 운영 성숙도가 충분해질 때. 트리거를 못 박지 않으면 폴링이 영구 부채로 굳는다.

### 실패 메시지의 운영 루프

처리에 실패하는 poison message는 무한 재시도로 파티션을 막거나, 조용히 버려져선 안 된다. V1의 PoisonMessage·스케줄러 재처리(v1 [[07.reservation]])를 계승해, **즉시 3회 재시도 → 지수 백오프 → DLQ 격리**의 단계를 둔다. DLQ로 격리된 건 **메시지 채널(예: Slack)로 알람을 발송**하고 기본은 수동 재생한다 — DLQ는 조용히 쌓이면 의미가 없으니 사람이 즉시 인지할 채널로 밀어내는 게 핵심이다. 자동 재생은 원인이 일시적임을 확신할 수 있을 때만이라 기본값으로 두지 않는다. (이의 여지: 재시도 횟수·백오프 곡선의 구체 값은 Design.)

### 컨슈머 그룹과 리밸런싱

read model·프로젝터는 같은 이벤트 스트림을 각자 다르게 소비한다. 그러니 **프로젝터별로 독립 컨슈머 그룹**을 둬서 fan-out 한다 — 한 프로젝터의 지연이 다른 프로젝터를 막지 않는다. 그룹 안에서는 competing consumers로 스케일하되 동시 처리 단위는 **파티션 수를 넘지 못한다**(초과 컨슈머는 놀 뿐). 리밸런싱은 정지를 최소화하는 cooperative-sticky를 기본으로 본다([[07-messaging-topology]]).

### 토픽의 retention과 재구축의 진실 원천

토픽을 얼마나 오래 보관할지는 재구축을 어디서 하느냐와 묶여 있다. read model을 토픽 처음부터 다시 흘려 재구축하려면 retention이 그만큼 길어야 한다. 하지만 V2에서 진실 원천은 **이벤트 스토어**다 — 토픽은 전달 채널일 뿐 영속 기록이 아니다. 그러니 토픽 retention은 **짧게** 두고, 재구축은 **스토어 리플레이**로 한다([[RFC-011-projection-rebuild-catchup]] 재구축 소스와 정합). 상태성 lookup 토픽처럼 "최신 상태"가 의미를 갖는 것만 log compaction을 쓴다. (이의 여지: 짧은 retention의 구체 기간과 compaction 대상 토픽 식별은 Design.)

### 무엇을 Kafka로 내보내는가 — 통합 이벤트(경계만 확정, 모양은 분리)

내부 도메인 이벤트를 그대로 Kafka에 흘리면, 내부 모델 변경이 외부 컨슈머를 깨뜨린다. Kafka로 나가는 건 **통합 이벤트(published language)**여야 하고, 내부 도메인 이벤트와 분리한다([[07-messaging-topology]]·[[02-write-model]]) — 이 *경계*는 여기서 못 박는다.

그러나 그 페이로드를 thin으로 둘지 fat으로 둘지, 직렬화 규약·스키마 버저닝을 어떻게 가져갈지는 결합도와 스키마 진화 전략이 얽혀 단독으로 결론 내기 애매하다. 이건 이 RFC에서 결론 내지 않고 **별도 RFC로 분리**한다(아래 §별도 RFC로 분리).

### 파티션 수 — 정책은 여기, 숫자는 운영

파티션 수는 순서 계약의 일부다 — 파티션 키가 `aggregate_id`인 이상, 파티션 수를 사후에 바꾸면 키 해싱이 재분배돼 순서 보장이 깨진다. 그래서 **고정 지향**으로 가고 보수적 초기값(일반 토픽 3, 고처리량 6~12 수준)을 둔다. 증설이 정말 필요하면 in-place 변경이 아니라 **새 토픽으로 마이그레이션**한다. 절대 초기값은 처리량 추정으로 Design에서 잡는다([[09.event-ordering-and-delivery-guarantee]]·[[07-messaging-topology]]·[[12.kafka-hosting-msk-vs-self-managed]]).

consumer lag(임계 숫자·SLI 단일화)은 [[RFC-002-read-model-consistency]]의 프로젝션 지연과 한 지표 체계라 단독으로 닫기 애매하다 — lag을 핵심 SLI로 본다는 *전제*만 메커니즘으로 깔고, 임계·SLI 체계 확정은 이 RFC에서 결론 내지 않고 **별도 RFC로 분리**한다(아래 §별도 RFC로 분리).

## Design으로 넘기는 것

- 파티션 절대 초기값(처리량 추정 기반)과 토픽 마이그레이션 절차.
- 컨슈머별 inbox 유지/생략 귀속(순서 역전 무풍지대 검증)과 inbox 보존 기간·GC 주기.
- 부수효과 유형별 디듀프/outbox/수동 귀속.
- DLQ 재시도 횟수·백오프 곡선·자동 재생 조건(알람 채널은 **메시지(Slack)로 확정** — 수치만 Design).
- 토픽 retention 구체 기간·compaction 대상 토픽 식별.
- relay 단일성·CDC 전환 기준은 필요 시 신규 ADR로 분리.

## 별도 RFC로 분리 — 여기서 결론 내지 않는 것

아래 둘은 이 RFC의 메커니즘 결정(전달 보장)과 결이 다르다 — 스키마 진화·관측 체계의 문제다. 이 문서에 끼워 어정쩡하게 두지 않고, 필요할 때 각각 **전용 RFC**로 연다(topical, not parked).

- **Kafka 통합 이벤트 페이로드 — thin/fat·직렬화·스키마 버저닝.** 통합 이벤트라는 *경계*는 확정됐고 그 *모양*만 분리한다. 스키마 진화([[10.event-schema-evolution]])·소비자 계약 테스트([[11-environments-and-testing]])와 묶어 다룬다.
- **consumer lag 임계·SLI 단일화.** [[RFC-002-read-model-consistency]]의 프로젝션 지연과 한 지표 체계라, 둘을 함께 다루는 관측 RFC로 모은다([[RFC-008-observability]]·[[RFC-007-deployment-infra-ops]]).

🌱 **토픽 목록 자체도 여기서 닫지 못한다.** 분할 축(컨텍스트/aggregate-type)은 정했지만, 실제 토픽 목록은 도메인 이벤트 카탈로그에 의존하고 그건 이벤트 스토밍이 선행해야 나온다([[07-messaging-topology]]·[[09.event-ordering-and-delivery-guarantee]]). 스토밍 이후 별도로 확정한다.

이 RFC가 닫히며 [[07-messaging-topology]]에 파티션·토픽·inbox·DLQ 섹션이 반영됐고, [[09.event-ordering-and-delivery-guarantee]]는 미결 해소 후 Proposed→Accepted 비준만 남는다.

## 관련 문서

- [[RFC-INDEX]] · [[07-messaging-topology]] · [[09.event-ordering-and-delivery-guarantee]] · [[09-deployment-runtime]] · [[12.kafka-hosting-msk-vs-self-managed]] · [[RFC-011-projection-rebuild-catchup]]
