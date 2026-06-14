# V2 TODO — 백로그 보드

> 아키텍처 본문([[index]])에서 **아직 다루지 않은** 항목을 캡처하는 곳.
> 하나씩 `now`로 올려 별도 파일로 확장 → **아키텍처에 영향 주는 항목은 `design_doc`/`adr`로 졸업**, 순수 운영은 여기 잔류.
> 학습 무게중심: **(a) 분산 패턴 + (c) DDD·도메인 모델링 = 1순위**, (b) 클라우드 네이티브 = 보조.

## 범례
- **상태**: `backlog` · `now` · `done`(완료/졸업) · **우선**: 상/중/하 (학습 레버리지 × ES·EDA 필요성)
- **졸업**: `ADR` / `design_doc` / `todo 잔류` · **역류** ✅ = V2 아키텍처 본문으로 되돌아옴(우선 검토)

## A. 인프라 · 런타임

| ID | 항목 | 우선 | 상태 | 졸업 | 역류 |
|----|------|------|------|------|------|
| T-01 | AWS / EKS 타깃 런타임 — MSK vs self-managed Kafka, event store 호스팅(RDS?) | 상 | done | 졸업: [[09-deployment-runtime]] · [[12.kafka-hosting-msk-vs-self-managed]] · [[13.db-hosting-and-read-write-topology]] | ✅ |
| T-02 | 로컬 개발 클러스터 — k3s, localstack(AWS 에뮬), compose 경계 | 중 | done | 졸업: [[11-environments-and-testing]] | ✅ |
| T-03 | Docker 정리 — 멀티스테이지, 이미지 슬림, compose 재정비 | 하 | backlog | todo 잔류 | |
| T-19 | **KEDA 이벤트 드리븐 오토스케일링** — consumer lag을 스케일 신호로(HPA보다 EDA 본질적) | 중 | backlog | todo 잔류 | |
| T-20 | **그레이스풀 셧다운 & 인플라이트 메시지 드레인** — 롤링/스케일다운 시 안전 커밋, at-least-once·멱등성 실제 성립 조건 | 상 | done | 졸업: [[07-messaging-topology]] | ✅ |

## B. 테스트 · 품질

| ID | 항목 | 우선 | 상태 | 졸업 | 역류 |
|----|------|------|------|------|------|
| T-04 | 테스트 전략 — 피라미드(testcontainers/integration/e2e) + **k6 부하**, ES 리플레이 성능 측정 | 상 | done | 졸업: [[11-environments-and-testing]] · [[14.testing-strategy]] | ✅ |
| T-05 | 카오스 / 장애 주입 — **Chaos Monkey for Spring Boot**(앱) + 인프라 레벨(k3s pod kill·broker 파티션), resilience 검증·학습 | 중 | backlog | 결정: [[14.testing-strategy]] (카테고리 확정; 도구·시나리오 TBD) | |

## C. 분산 시스템 패턴 (EDA/ES — 학습 1순위 a)

| ID | 항목 | 우선 | 상태 | 졸업 | 역류 |
|----|------|------|------|------|------|
| T-06 | Saga / 프로세스 매니저 — 컨텍스트 간 트랜잭션, 안무 vs 오케스트레이션 | 상 | done | 졸업: [[06-consistency-and-sagas]] · [[08.saga-orchestration-vs-choreography]] | ✅ |
| T-07 | 멱등성 · 재처리 · 중복 제거 — consumer 멱등키 | 상 | backlog | 결정: [[09.event-ordering-and-delivery-guarantee]] (inbox/멱등키 메커니즘 확정; inbox 보존·GC·자연멱등 생략 기준 TBD) | ✅ |
| T-08 | 이벤트 스키마 진화 / 레지스트리 — Avro/Protobuf, 버저닝 | 상 | done | 졸업: [[10.event-schema-evolution]] | ✅ |
| T-09 | 프로젝션 메커니즘 — 재구축, catch-up, read-your-writes, 멱등 프로젝터 | 상 | backlog | design_doc | ✅ |
| T-14 | **ES PII / GDPR crypto-shredding** — append-only는 삭제 불가, 키 폐기로 복호 불능화 | 상 | done | 졸업: [[11.es-pii-crypto-shredding]] · [[08-event-store-lifecycle]] | ✅ |
| T-15 | **Kafka 운영** — 파티셔닝·순서 보장 키(`aggregate_id`), 컨슈머 그룹·리밸런싱, lag·백프레셔, competing consumers | 상 | done | 졸업: [[07-messaging-topology]] · [[09.event-ordering-and-delivery-guarantee]] | ✅ |
| T-16 | **스냅샷 운영 전략** — 생성 주기, 스키마 변경 시 무효화·재생성, 스냅샷-이벤트 정합성 검증 | 상 | done | 졸업: [[08-event-store-lifecycle]] | ✅ |
| T-17 | **temporal / as-of 조회** — 특정 시점 상태 재구성(시간여행), 감사·디버깅 | 중 | done | 졸업: [[08-event-store-lifecycle]] | ✅ |
| T-18 | **이벤트 스토어 보존·백업·DR·아카이빙** — 영구 성장 테이블 파티셔닝·콜드 스토리지, 이벤트 손실=시스템 손실 | 중 | done | 졸업: [[08-event-store-lifecycle]] (보존·파티셔닝·콜드 스토리지 확정; DR 런북 TBD) | ✅ |

## D. 관측성 · 운영

| ID | 항목 | 우선 | 상태 | 졸업 | 역류 |
|----|------|------|------|------|------|
| T-10 | 관측성 — OTel 분산 추적(Kafka 횡단), 메트릭, 구조적 로그, correlation id | 상 | backlog | 결정: [[10-observability]] (id 전파 규약·헤더 슬롯·**OSS 스택 OTel+Prometheus/Grafana/Tempo/Loki**(vendor-neutral) 확정; 도구 배선 TBD) | |
| T-11 | CI/CD · GitOps — ArgoCD/Flux, progressive delivery | 중 | backlog | todo 잔류 | |
| T-12 | SLI / SLO — 지표 정의, k6 연계 | 중 | backlog | todo 잔류 | |

## E. 보안

| ID | 항목 | 우선 | 상태 | 졸업 | 역류 |
|----|------|------|------|------|------|
| T-13 | 시크릿 관리 / 인증 확장 — Vault·AWS Secrets Manager, OAuth2/OIDC | 하 | backlog | todo 잔류 | |

## F. DDD · 도메인 모델링 (학습 1순위 c)

| ID | 항목 | 우선 | 상태 | 졸업 | 역류 |
|----|------|------|------|------|------|
| T-21 | **DDD 도메인 모델링 심화** — 빈약 도메인(W-1) 극복 기법 묶음. 펼치면 ↓ 체크리스트. 이벤트 스토밍 재실시 도메인 사이클의 입력 | 상 | backlog | design_doc(05) + 도메인 사이클 | ✅ |

---

## 확장 체크리스트 (각 항목을 펼칠 때의 하위 주제)

> 상위 T-xx를 `now`로 올려 별도 파일로 뺄 때, 아래를 그 파일의 목차/체크리스트로 쓴다. (병렬 리스트업 산출물 — 누락 방지용 캡처)

### T-06 Saga
- 사가 타임아웃·만료 보상 트리거 (예: 예약 임시점유 만료) [하]

### T-07 멱등성·재처리
- inbox 패턴 (컨슈머측 멱등 수신 저장 — outbox의 대칭 짝) [상]
- exactly-once의 한계 → effectively-once 수용 근거 (Kafka EOS 비용·보장 범위) [중]
- DLQ·poison message 재처리 운영 루프 (수동/자동 재생, 알림) [중]
- CDC(Debezium) vs 애플리케이션 Outbox 비교 (듀얼라이트·결합도·운영 복잡도) [중]

### T-08 스키마 진화
- 이벤트 업캐스팅 파이프라인 (리플레이 시 구버전→신버전 변환; 레지스트리=wire, 업캐스팅=리플레이 시점) [상]

### T-09 프로젝션
- read-your-writes / 모노토닉 읽기 보장 (동기 프로젝션 예외·버전 토큰·sticky read 선택) [중]
- readiness probe = "Kafka 연결 + catch-up 완료" 연동 (트래픽 라우팅과 정합) [중]

### T-10 관측성
- correlation id + causation id 전파 (이벤트 메타데이터에 심어 커맨드→이벤트 사슬 추적; 추적 백엔드와 별개) [중]

### T-11 CI/CD·배포
- DB 마이그레이션 in k8s (Flyway Job/initContainer, 다중 파드 단일 실행 보장) [중]
- 멀티 환경 config/secrets (**Helm** 차트+values — local/stage/prod 분기) [중]
- 이미지 취약점 스캐닝 & SBOM (Trivy/Grype, CI 게이트) [하]

### T-21 DDD 도메인 모델링 심화
- 애그리거트 경계 휴리스틱 — "트랜잭션 1 = 애그리거트 1", ID 참조로 강결합 분리 [상]
- 불변식 배치 규칙 — 진짜 불변식(애그리거트 내부) vs 결과적 일관성(이벤트) [상]
- 컨텍스트 매핑 — ACL / Conformist / OHS / Published Language (이벤트=Published Language) [상]
- Specification 패턴 — "취소 가능 기한" 등 분기 규칙을 합성 가능한 객체로 (흩어진 validate 재배치처) [상]
- Value Object 심화 — 자가검증·불변·동등성으로 애그리거트를 얇게 [상]
- 도메인 서비스 triage 워크시트 — 16개 V1 서비스 1:1 판정, 잔존 개수를 빈약도 지표로 [상]
- 불변 애그리거트 전이 — `copy()` 기반 apply, `private constructor`+팩토리 [중]
- Command/Event 명명·모델링 — 명령형(`CancelReservation`) vs 과거형(`ReservationCancelled`), 페이로드 최소화 [중]
- 유비쿼터스 언어 용어집 — 컨텍스트별 glossary(코드 식별자↔도메인 용어) [중]
- Example Mapping — 이벤트 스토밍 보완(커맨드→이벤트를 규칙·예시·질문으로 분해해 불변식 발굴) [중]
- Policy / Process Manager — 이벤트 반응 규칙을 도메인 정책으로, 멀티스텝은 프로세스 매니저로 [중]
- 애그리거트 버전 낙관 락 — "애그리거트=일관성 경계"가 동시성까지 책임(한계 #6 해소) [하]

---

> 정렬: 상위 표는 우선순위 컬럼으로 스캔, 펼칠 때 체크리스트로 깊이 확보. 역류 ✅ 항목은 확장 시 `design_doc`/`adr` 졸업 검토 우선.
