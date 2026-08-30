# 측정 환경

모든 시나리오는 아래 환경에서 측정한다. 값이 바뀌면 이 문서를 갱신하고,
이전 결과는 비교 대상에서 제외한다.

> 이 문서의 수치는 **2026-08-29 측정 시점에 실제로 조회한 값**이다.
> 추정이나 이전 문서에서 옮겨온 값이 아니다. 확인하지 못한 항목은 그렇게 표시했다.

## 1. 호스트

| 항목 | 값 | 확인 방법 |
|---|---|---|
| 기기 | Mac16,8 — Apple M4 Pro | `sysctl hw.model` |
| 코어 | 12 | `sysctl hw.ncpu` |
| 메모리 | 48 GB | `sysctl hw.memsize` |
| OS | macOS 26.5.2 (25F84) | `sw_vers` |
| 임시 포트 | 49152 – 65535 (**16,384개**) | `sysctl net.inet.ip.portrange.*` |
| `ulimit -n` | 1,048,576 (러너가 200,000으로 설정) | `ulimit -n` |

**부하 발생기와 서버가 같은 장비에 있다.** 네트워크 지연이 0에 가깝고 CPU를 공유하므로,
실서비스 절대값이 아니라 **구조 비교**로만 해석한다.

## 2. 소프트웨어

| 항목 | 값 |
|---|---|
| JDK | OpenJDK 21.0.10 LTS |
| Spring Boot | 3.4.5 |
| Kotlin | 2.0.10 |
| k6 | v2.2.0 (go1.26.5, darwin/arm64) |
| Python | 3.14.4 (집계 스크립트) |
| mysql 클라이언트 | 9.0.1 (Homebrew) |

## 3. 데이터 면 (Docker)

| 항목 | 값 |
|---|---|
| Docker Engine | 29.2.1 |
| VM CPU | **6** |
| VM 메모리 | **15.6 GB** |
| 스토리지 드라이버 | overlayfs |
| Compose 프로젝트 | `chore-performance-test-after` |

### 컨테이너

| 서비스 | 이미지 | 실제 버전 | 포트 |
|---|---|---|---|
| MySQL | `mysql:latest` | **26.7.0** | 3306 |
| Redis | `redis:latest` | **8.10.1** | 6379 |
| Kafka ×3 | `apache/kafka:latest` | — | 9092/9093/9094 |

> `:latest` 태그를 쓰고 있어서 **이미지를 다시 받으면 버전이 조용히 바뀐다.**
> before/after 비교 도중에는 `docker pull`을 하지 않는다.

### MySQL 설정 (조회값)

| 변수 | 값 |
|---|---|
| `max_connections` | 151 |
| `innodb_buffer_pool_size` | **134,217,728 (128 MB — 기본값)** |
| `innodb_flush_log_at_trx_commit` | 1 (커밋마다 fsync) |
| `transaction_isolation` | REPEATABLE-READ |

버퍼풀이 기본값 128MB지만, 이 실험의 데이터셋은 좌석 수십~수백 행이라 전부 메모리에 든다.
**측정 결과로도 DB가 병목이 아님이 확인됐다** (아래 4절).

## 4. 부하 중 실측 포화도 — DB는 병목이 아니었다

`_lib/infra-sample.sh`가 k6 실행 내내 1초 간격으로 기록한 값의 회차별 최대치
(S1 단건 오픈런, 각 5회).

| 동시 인원 | MySQL CPU | Redis CPU | DB 실행 중 스레드 | DB 커넥션 | TIME_WAIT |
|---:|---:|---:|---:|---:|---:|
| 200 | 6.7% | 3.8% | 3 | 12 | 372 |
| 1,000 | 8.0% | 11.8% | 3 | 12 | 1,039 |
| 3,000 | 13.7% | 11.4% | 3 | 11 | 3,041 |
| 8,000 | 14.6% | 11.8% | 4 | 12 | 8,762 |
| 10,000 | 13.7% | 11.9% | 3 | 11 | 9,598 |

**세 가지가 동시에 확인된다.**

1. **MySQL CPU가 14%를 넘지 않는다.** VM이 6 vCPU이므로 컨테이너 상한은 600%다.
   `infra-sample.sh`는 480%(80%)를 넘으면 `dbSaturated`를 켜는데, 한 번도 켜지지 않았다.
2. **DB 커넥션이 인원과 무관하게 최대 12, 실행 중인 쿼리는 3~4개다.**
   10,000명이 몰려도 앱이 DB에 그 이상의 동시 작업을 준 적이 없다.
   → **DB가 굶어서 느린 게 아니라, 앱이 DB를 그만큼밖에 안 쓴다.**
3. **TIME_WAIT이 인원에 비례해 늘지만 9,598에서 멈춘다.**
   `generatorPortPressure` 임계값 12,000 미만이라 **발생기 포트는 마르지 않았다.**

→ 관측된 한계는 인프라도 발생기도 아니고 **전부 애플리케이션 안**이다.
"장비가 약해서 그렇다"는 반론은 이 표로 막힌다.

## 5. 측정 대상 앱

| 라벨 | 경로 | 브랜치 |
|---|---|---|
| before | `~/Downloads/port/prototype-reservation-system` | `chore/performance-test` |

`preflight.sh before`가 8081 리스닝 프로세스의 CWD가 이 경로인지 검사한다.
1차 측정에서 다른 빌드가 떠 있는 걸 놓쳐 결과가 오염된 적이 있어 자동화했다.

### 고정 기동 인자

측정 조건이지 코드 변경이 아니므로 소스에는 넣지 않는다.

```bash
java -jar adapter-module/build/libs/adapter-module-0.0.1-SNAPSHOT.jar \
  --server.port=8081 \
  --server.tomcat.threads.max=200 \
  --server.tomcat.accept-count=100 \
  --server.tomcat.max-connections=8192 \
  --decorator.datasource.enabled=false \
  --security.jwt.properties.expire-time=31536000000
```

| 인자 | 기본값 | 지정값 | 이유 |
|---|---:|---:|---|
| `tomcat.threads.max` | 200 | 200 | 기본값 유지하되 **명시**해서 재현 보장 |
| `tomcat.accept-count` | 100 | 100 | 동일 |
| `tomcat.max-connections` | 8192 | 8192 | 동일. **인원 사다리를 이 값 기준으로 잡았다** |
| `decorator.datasource.enabled` | true | **false** | p6spy 프록시를 끈다. 켜두면 모든 SQL이 프록시를 거쳐 프로파일러를 켠 채 재는 셈 |
| `jwt.expire-time` | 300,000ms | **31,536,000,000ms (1년)** | 토큰을 스위트 시작 전 한 번 발급해 재사용. 짧으면 긴 런 중간에 401이 나서 측정이 깨진다 |

**만료 1년은 측정에 영향이 없다.** `JWTProvider`는 만료 시각만 계산하고, 검증 경로는
서명 확인 + 만료 비교라 값의 크기와 무관하게 비용이 같다. `expireTime`이 `Long`이라
오버플로도 없다.

### 클라이언트 측 고정 조건

| 항목 | 값 | 이유 |
|---|---:|---|
| k6 요청 타임아웃 | **60s** | **"느림"과 "실패"의 경계를 정하는 값이다.** 실제 사용자도 LB도 무한정 기다리지 않는다. 명시하지 않으면 k6 기본값에 결과가 좌우된다 |
| `ulimit -n` (러너) | 200,000 | `run.sh`가 올린다 |
| 유저 풀 | 최대 군중 이상 | 모자라면 토큰이 돌려쓰기 되어 같은 사람이 동시에 여러 번 예약하게 되고, "몇 명이 좌석을 받았나"가 흐려진다 |

## 6. 코드 조건

### `@RateLimiter`는 발동하지 않는다 (확인함)

`RateLimiterAspect`는 코드에 남아 있지만 **잡을 대상이 없다.**
`src/main` 전체에서 `@RateLimiter`가 나오는 유일한 자리는 애스펙트 내부의 에러 메시지
문자열이다 (`adapter-module/.../config/aspect/RateLimiterAspect.kt:132`).

```bash
grep -rn "@RateLimiter" --include="*.kt" */src/main   # → 위 한 줄만 나온다
```

→ **스로틀링이 처리량 상한을 만들었을 가능성은 배제된다.**

### 락 획득은 트랜잭션 바깥에서 일어난다

`DistributedLockAspect`가 `@Order(HIGHEST_PRECEDENCE)`이고 `@Transactional`은 기본
`LOWEST_PRECEDENCE`이므로, 순서는 `락 획득 → 트랜잭션 시작 → 본문 → 커밋 → 락 해제`다.

→ 락 대기 중에는 DB 커넥션을 물지 않는다. 실측 DB 커넥션 최대 12가 이것과 일치한다.
대신 **락 대기가 Tomcat 워커 스레드를 점유한다** (워커는 200개).

### 커넥션 풀 크기 — 미확인 항목

`adapter-module`의 yml에 `spring.datasource.hikari.*` 설정이 **없다**(확인함).
`FlywayConfig`가 `@FlywayDataSource` 한정자로 `DataSource` 빈을 선언하는데,
이것이 `DataSourceAutoConfiguration`을 back-off 시켜 앱 전체가 Flyway용 DataSource
위에서 도는지는 **이번에 확인하지 못했다** (`@ConditionalOnProperty(spring.flyway.url)`
조건부이므로 활성 프로파일에 따라 달라진다).

**측정에는 영향이 없다.** 실측 DB 커넥션 최대 12 / 실행 중 스레드 3~4는
풀 크기가 무엇이든 앱이 그만큼밖에 안 쓴다는 뜻이고, before/after 동일 조건이다.
다만 **"풀 크기가 10이라서 느리다"고 주장하려면 먼저 이걸 확인해야 한다.**

## 7. 관측하지 않는 것과 그 이유

서버 내부 지표(Actuator의 Tomcat 스레드/힙/커넥션 풀)는 **관측하지 않는다.**
이 실험이 답하려는 건 "사용자가 무엇을 겪는가"이고, 내부 지표는 그 답이 아니라
원인 해설이라 결론을 바꾸지 못한다.

관측하는 건 세 가지다.

| 층 | 지표 | 출처 |
|---|---|---|
| 클라이언트 | 좌석 획득, 대기 분포, 실패 사유별 분류, 발사 스큐 | k6 |
| 데이터 | 오버부킹, 미판매, 정착 시간 | MySQL 직접 조회 (`_lib/integrity.sh`) |
| 인프라 | 컨테이너 CPU, DB 스레드, 호스트 부하, TIME_WAIT | `_lib/infra-sample.sh` |

인프라를 재는 건 성능을 보려는 게 아니라 **"인프라가 병목이 아니었다"를 증명**하기
위해서다. 부하가 끝난 뒤 `docker stats`를 찍어봐야 이미 idle이라 아무것도 증명하지
못한다 — 부하 중에 찍어야 한다.

## 8. 측정 전 점검

### 컨테이너 생존 (실제로 겪은 함정)

컨테이너가 조용히 죽어 있는 경우가 있다 (`exit 137` = SIGKILL, Docker Desktop 재시작
또는 VM OOM). **앱은 그 상태에서도 8081에 계속 떠 있다.**

이때 앱의 커넥션 풀이 회복되지 못해 **DB 조회가 전부 실패하고, 그게 인증 실패(401)로
떨어진다.** "비밀번호 해시가 틀렸나" 쪽으로 진단이 새기 딱 좋다.

```bash
docker ps                                    # mysql/redis/kafka×3 이 Up 인지
docker start chore-performance-test-after-{mysql-1,redis-1,kafka-1-1,kafka-2-1,kafka-3-1}
# 그리고 앱을 반드시 재기동한다 — 풀은 스스로 회복하지 않았다
```

`preflight.sh`가 MySQL/Redis 연결은 검사하지만 **앱 재기동은 자동화되어 있지 않다.**
preflight 통과 후에도 로그인 한 번으로 확인하는 게 안전하다.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X PUT http://localhost:8081/api/v1/user/sign-in \
  -H 'Content-Type: application/json' -d '{"loginId":"k6perf0001","password":"K6perf!2026"}'
# 200 이어야 한다
```

### 전체 점검

```bash
./performance_test/_lib/preflight.sh before
```

| 검사 | 실패 시 |
|---|---|
| 8081 리스닝 | 앱 미기동 → 측정 중단 |
| 앱 CWD가 before 경로인가 | 다른 빌드 측정 위험 → 중단 |
| MySQL / Redis 연결 | 컨테이너 사망 → 중단 |
| 다른 k6 프로세스 없음 | 동시 측정은 같은 DB 픽스처를 공유해 조용히 오염시킨다 → 중단 |
| `ulimit -n` | 낮으면 경고 (러너가 올리지만 고 VU에서 발생기 한계를 잴 수 있다) |
