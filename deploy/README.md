# deploy/ — 로컬 k3s(패리티) 인프라

이 디렉토리는 **운영(AWS EKS) 환경을 노트북에서 똑같은 모양으로 흉내 내는** 로컬 쿠버네티스 환경이다.
쿠버네티스를 처음 써본다면 이 문서를 위에서부터 그냥 따라오면 된다. 개념 → 설치 → 실행 → 둘러보기 → 문제 해결 순서다.

---

## 0. 이게 왜 필요한가 (compose 랑 뭐가 다른가)

이 프로젝트에는 로컬 환경이 **두 개** 있다. 목적이 다르다.

| | `compose.yaml` (리포 루트) | `deploy/` (여기, k3s) |
|---|---|---|
| 답하는 질문 | **"코드가 도는가"** (속도) | **"운영처럼 도는가"** (패리티) |
| 실행 도구 | `docker compose up` | `make up` (k3d + helm) |
| 구조 | 컨테이너 몇 개가 한 네트워크에 평평하게 | 운영과 똑같이 워크로드가 분리된 쿠버네티스 |
| 언제 쓰나 | 매일 코딩하며 빠른 피드백 | PR/머지 게이트·E2E·"운영에서 처음 깨지는 버그"를 로컬로 당길 때 |

> ⚠️ **매 코드 변경마다 `deploy/`(k3s)를 띄우지 말 것.** 무겁다. 평소엔 compose로 개발하고,
> "운영 배포에서만 재현되는 문제"를 잡을 때만 여기를 쓴다. (근거: `docs/v2/design_doc/DESIGN-012` §2)

지금 이 단계에서 뜨는 것은 **데이터 면(DB·캐시·메시지 큐 등)** 과 **엣지(입구)** 다.
실제 애플리케이션(command/query 서비스 등)은 아직 코드가 없어서(Phase 7 예정) 안 뜬다 — 그것들이 **착지할 바닥**을 먼저 까는 것이다.

---

## 1. 용어 사전 (처음이면 여기부터)

쿠버네티스 용어가 낯설면 이 표를 옆에 두고 읽으면 된다.

| 용어 | 한 줄 설명 | 이 프로젝트에서 |
|---|---|---|
| **Docker** | 컨테이너(격리된 프로세스)를 돌리는 엔진 | 모든 것의 바닥. Docker Desktop 이 떠 있어야 함 |
| **Kubernetes (k8s)** | 컨테이너를 여러 개 자동으로 배치·관리하는 오케스트레이터 | 운영에서 쓰는 그것 (AWS EKS) |
| **k3s** | 가볍게 줄인 쿠버네티스 (기능은 거의 동일) | 로컬에서 EKS 를 흉내 내는 몸통 |
| **k3d** | k3s 를 **Docker 컨테이너 안에서** 띄워주는 도구 | `make up` 이 내부에서 부르는 것 |
| **node (노드)** | 컨테이너가 실제로 도는 "머신" (여기선 Docker 컨테이너 1개) | 로컬은 노드 1개 |
| **Pod (파드)** | 쿠버네티스가 굴리는 최소 단위. 컨테이너 1개(보통)를 감쌈 | `mysql-command`, `redis` 등이 각각 파드 |
| **Deployment** | "이 파드를 N개 항상 떠 있게 유지해라"는 명세 | MySQL·Redis·localstack 각각 |
| **Service** | 파드에 접근할 고정된 내부 주소(DNS)·로드밸런서 | `mysql-command.reservation.svc` 같은 주소 |
| **Namespace (ns)** | 리소스를 묶는 폴더 같은 논리 구획 | 대부분 `reservation` 네임스페이스 |
| **PVC** | 파드가 재시작해도 안 날아가는 디스크 요청 | MySQL·Redis 데이터 저장용 |
| **Helm** | 쿠버네티스 매니페스트(YAML 묶음)를 "차트"로 패키징하는 도구 (앱의 apt/brew 같은 것) | 데이터 면을 `charts/data-plane` 차트로 설치 |
| **Helm chart / release** | 차트 = 템플릿 묶음, release = 그 차트를 클러스터에 실제로 설치한 인스턴스 | `helm ls -A` 로 목록 확인 |
| **Operator / CRD** | 특정 소프트웨어(Kafka 등)를 대신 운영해주는 컨트롤러(Operator)와, 그게 이해하는 새 리소스 타입(CRD) | Strimzi·Envoy Gateway 가 Operator |
| **Strimzi** | 쿠버네티스 위에서 **Kafka** 를 운영해주는 Operator | `Kafka` 라는 CRD 하나 던지면 브로커를 알아서 띄움 |
| **Gateway API / Envoy Gateway** | 클러스터 입구(엣지)에서 트래픽을 라우팅하는 표준(Gateway API)과 그 구현(Envoy) | 운영 엣지. 지금은 Operator 만, 라우트는 Phase 7 |
| **localstack** | AWS 서비스(S3·Secrets 등)를 로컬에서 흉내 내는 에뮬레이터 | AWS-고유 기능만 이걸로 대체 |
| **kubectl** | 쿠버네티스에 명령을 내리는 CLI | 상태 확인·로그·디버깅의 주 도구 |
| **context** | kubectl 이 "어느 클러스터에 말하는가" 설정 | 이 클러스터는 `k3d-reservation` |

---

## 2. 선행 준비 (한 번만)

```bash
# 1) 도구 설치 (kubectl 은 이미 있을 수 있음)
brew install k3d helm

# 2) 설치 확인
docker --version        # Docker 있는지
k3d version             # k3d 있는지
helm version            # helm 있는지
kubectl version --client  # kubectl 있는지

# 3) Docker Desktop 실행 (맥이면)
open -a Docker
docker info             # 에러 없이 정보가 나오면 준비 완료
```

`docker info` 가 에러 나면 Docker Desktop 이 아직 안 뜬 것 — 고래 아이콘이 "running" 될 때까지 기다린다.

---

## 3. 빠른 시작

```bash
cd deploy
make up        # 전부 생성: 클러스터 → operator → 데이터 면 (수 분 소요, 이미지 다운로드)
make status    # 잘 떴는지 확인
```

`make up`(= `bootstrap.sh`)이 순서대로 하는 일:

1. **k3d 클러스터 생성** — Docker 안에 k3s 노드 1개를 띄운다. (`k3d/cluster.yaml` 설정 사용)
2. **Strimzi operator 설치** — Kafka 를 운영해줄 컨트롤러. (Kafka CR 보다 먼저 있어야 함)
3. **Envoy Gateway 설치** — 엣지(입구) 컨트롤러 + GatewayClass·Gateway.
4. **데이터 면 설치** — MySQL×2·Redis·localstack·Kafka 를 Helm 차트로 한 번에.

> `make up` 은 **멱등**하다 — 이미 있는 건 건너뛰므로 여러 번 실행해도 안전하다.

정상이면 `make status` 에서 모든 파드가 `Running`, Kafka 가 `READY=True` 로 보인다.

---

## 4. 둘러보기 (kubectl 기본기)

쿠버네티스 상태는 전부 `kubectl` 로 본다. 자주 쓰는 것만:

```bash
# 이 클러스터를 보도록 context 지정 (한 번)
kubectl config use-context k3d-reservation

# 파드 목록 (STATUS 가 Running 이어야 정상)
kubectl -n reservation get pods

# 특정 파드가 왜 안 뜨는지 (이벤트·에러 원인)
kubectl -n reservation describe pod <파드이름>

# 파드 로그 보기 (-f 는 실시간)
kubectl -n reservation logs <파드이름> -f

# 파드 안으로 들어가 명령 실행
kubectl -n reservation exec -it deploy/redis -- redis-cli ping

# Kafka / 커스텀 리소스 상태
kubectl -n reservation get kafka,kafkanodepool

# 엣지(Gateway) 상태
kubectl -n reservation get gateway
```

읽는 법: `get pods` 의 `STATUS` 열이 핵심이다 —
`Running`(정상) / `Pending`(스케줄 대기, 보통 이미지 다운로드 중) / `CrashLoopBackOff`(계속 죽음 → `logs` 로 원인 확인) / `ImagePullBackOff`(이미지 못 받음).

---

## 5. 각 서비스에 접속하기

**두 가지 경로**가 있다.

### (a) 클러스터 내부에서 (앱이 쓰는 정상 경로)
클러스터 안 파드끼리는 Service DNS 주소로 붙는다. Phase 7 앱은 이렇게 붙는다:

| 서비스 | 내부 주소 |
|---|---|
| command MySQL | `mysql-command.reservation.svc:3306` |
| query MySQL | `mysql-query.reservation.svc:3306` |
| Redis | `redis.reservation.svc:6379` |
| localstack | `localstack.reservation.svc:4566` |
| Kafka | `reservation-kafka-bootstrap.reservation.svc:9092` |

### (b) 내 노트북에서 (디버깅용 port-forward)
호스트에서 직접 붙어 확인하고 싶을 때. `make pf-*` 가 터널을 연다:

```bash
make pf-mysql-command   # localhost:3306 -> command MySQL
make pf-mysql-query     # localhost:3307 -> query MySQL
make pf-redis           # localhost:6379 -> Redis
make pf-kafka           # localhost:9092 -> Kafka
# 각 명령은 터널을 연 채 유지된다(Ctrl+C 로 종료). 다른 터미널에서 접속:
#   mysql -h127.0.0.1 -P3306 -uroot -pverysecret reservation_command
#   redis-cli -h 127.0.0.1 -p 6379 ping
```

> 왜 nodePort 로 전부 안 뚫나? 패리티 환경에서 **정상 경로는 (a) 내부 DNS** 다. 호스트 노출은 디버깅 예외라 필요할 때만 port-forward 로 연다.

---

## 6. 지금 뜨는 것들의 지도 (로컬 ↔ 운영)

로컬은 운영 토폴로지를 **모양 그대로** 미러링하되 **용량만** 줄인다.

| 운영 (EKS, DESIGN-010) | 로컬 (여기) | 무엇을 줄였나 |
|---|---|---|
| command MySQL (event_store/state/Outbox) + HA standby | `mysql-command` 파드 1개 | HA standby 생략 |
| query MySQL (프로젝션 read model) + HA | `mysql-query` 파드 1개 | HA 생략 (분리는 유지!) |
| Strimzi Kafka 다중 브로커 | `reservation-combined-0` 브로커 1개 | 브로커·복제 1로 축소 |
| ElastiCache Redis | `redis` 파드 1개 | 단일 인스턴스 |
| AWS S3/Secrets | `localstack` | 실제 AWS → 에뮬레이터 |
| Envoy Gateway (엣지) | `envoy-gateway` + Gateway | 라우트는 Phase 7 |

> **절대 줄이지 않는 것**: command/query 가 **물리적으로 분리된 DB** 라는 사실, 워크로드가 별도 파드라는 사실.
> 이건 "용량"이 아니라 "토폴로지"라, 여기서 합쳐버리면 패리티 환경의 존재 이유가 사라진다.

---

## 7. 문제 해결 (자주 겪는 것들)

| 증상 | 원인 / 해결 |
|---|---|
| `make up` 이 "Docker 데몬이 떠 있지 않음" 으로 멈춤 | Docker Desktop 실행: `open -a Docker`, `docker info` 로 확인 후 재실행 |
| 파드가 오래 `Pending` | 보통 이미지 다운로드 중. `kubectl -n reservation describe pod <이름>` 하단 Events 확인 |
| 파드가 `CrashLoopBackOff` | `kubectl -n reservation logs <이름>` 로 앱 에러 확인 |
| 파드가 `ImagePullBackOff` | 이미지 태그 오타/네트워크. `values.yaml` 의 이미지 태그 확인 |
| Kafka 가 `READY` 안 됨 | Strimzi operator 로그: `kubectl -n reservation logs deploy/strimzi-cluster-operator` |
| 뭔가 꼬였다, 처음부터 다시 | `make down` 후 `make up` (클러스터째 지우고 재생성 — 로컬이라 안전) |
| kubectl 이 다른 클러스터를 봄 | `kubectl config use-context k3d-reservation` |

**완전 초기화:**
```bash
make down     # 클러스터·데이터·레지스트리 전부 삭제
make up       # 새로 생성
```
로컬 환경이라 언제든 지웠다 다시 만들어도 된다 (데이터는 실습용이라 아까울 게 없다).

---

## 8. 정리 (끝났을 때)

```bash
make down     # 클러스터째 삭제. Docker 리소스도 정리됨
```

Docker Desktop 자체는 계속 떠 있어도 되고, 안 쓰면 종료해도 된다.

---

## 9. 파일별 지도

```
deploy/
  README.md                       # 이 문서
  Makefile                        # make up/down/status/pf-* 명령 모음
  bootstrap.sh                    # make up 의 실체 — 클러스터+operator+데이터면 생성 (멱등)
  teardown.sh                     # make down 의 실체 — 클러스터 삭제
  k3d/
    cluster.yaml                  # k3d 클러스터 정의 (노드 수·포트·레지스트리·traefik off)
  charts/
    data-plane/                   # 데이터 면 Helm 차트
      Chart.yaml                  #   차트 메타데이터
      values.yaml                 #   ★ 설정값(이미지 태그·DB 이름·용량) — 여기만 고치면 됨
      templates/
        _helpers.tpl              #   공통 라벨 헬퍼
        mysql.yaml                #   command/query MySQL (values 의 instances 순회)
        redis.yaml                #   Redis
        localstack.yaml           #   localstack
        kafka.yaml                #   Strimzi Kafka CR (KRaft 단일 브로커)
    edge/                         # 엣지(입구) Helm 차트
      Chart.yaml
      values.yaml
      templates/
        gatewayclass.yaml         #   GatewayClass(envoy) — 엣지 종류 선언
        gateway.yaml              #   Gateway — HTTP :80 입구 (라우트는 Phase 7 에 앱과 함께)
```

**설정을 바꾸고 싶으면** 거의 항상 `charts/*/values.yaml` 만 고치면 된다. 예: MySQL 버전 올리기 →
`charts/data-plane/values.yaml` 의 `mysql.image` 수정 후 `make data-plane`.

---

## 10. 다음 단계 (Phase 7 착지)

지금은 **바닥(데이터 면) + 입구(엣지 Operator)** 까지다. 앱 코드가 생기면(Phase 7) 그 위에 올린다:

1. 앱 워크로드 Helm 차트 — command/query/projector/relay/auth (단일 이미지·다중 프로파일, DESIGN-012 §4)
2. `charts/edge` 에 **HTTPRoute** 추가 — Gateway 입구 → 앱 서비스로 라우팅
3. 로컬 레지스트리(`localhost:5001`)에 슬림 이미지 푸시 파이프라인

---

## 11. Gradle/CI 에 영향 없음

이 디렉토리는 Gradle 프로젝트가 아니다 (`settings.gradle.kts` 미등록, `build.gradle.kts` 없음).
GitHub `build.yaml` 워크플로우의 `./gradlew build -x test` 에 전혀 영향을 주지 않는다.

## 참고 문서

- `docs/v2/design_doc/DESIGN-012` — 환경·테스트 전략 (compose vs k3s vs k6)
- `docs/v2/design_doc/DESIGN-010` — 목표 런타임 토폴로지 (운영 EKS)
- `docs/v2/adr/ADR-012` (Kafka self-managed) · `ADR-013` (DB 분리) · `ADR-024`/`ADR-026` (엣지·워크로드 배치)
