# Jenkins 배치 실행 완전 가이드

## 목차
1. [배경: 왜 필요한가?](#배경-왜-필요한가)
2. [Jenkins가 하는 일](#jenkins가-하는-일)
3. [배치 애플리케이션 기본 개념](#배치-애플리케이션-기본-개념)
4. [실행 방식 비교](#실행-방식-비교)
5. [실전 시나리오](#실전-시나리오)
6. [Quartz vs Jenkins](#quartz-vs-jenkins)
7. [결론 및 권장사항](#결론-및-권장사항)

---

## 배경: 왜 필요한가?

### 상황
매일 새벽 2시에 정산 배치를 돌려야 하는 상황

### 문제점
- 개발자가 매일 새벽 2시에 수동으로 실행? → **불가능**
- 서버에 cron 설정? → 여러 서버에 흩어지면 **관리 지옥**
- 실패하면? → **누가 알아채나?**
- 언제 실행됐는지 기록? → **로그 찾기 힘듦**

### 해결
Jenkins로 중앙화된 배치 실행 관리

---

## Jenkins가 하는 일

```
Jenkins = 배치 실행 스케줄러 + 실행 버튼 + 모니터링
```

### 핵심 기능

1. **스케줄링**: 매일 새벽 2시에 자동 실행
2. **수동 실행**: 버튼 클릭으로 즉시 재실행
3. **파라미터**: 날짜 같은 값 UI로 입력
4. **이력 관리**: 언제 실행, 성공/실패, 로그 전부 저장
5. **알림**: 실패 시 Slack/이메일 자동 발송

---

## 배치 애플리케이션 기본 개념

### 웹 애플리케이션 vs 배치

**웹 애플리케이션 (API 서버)**
```kotlin
@SpringBootApplication
class ApiServer

fun main(args: Array<String>) {
    SpringApplication.run(ApiServer::class.java, *args)
    // 여기서 끝나지 않고 계속 떠있음
    // 요청 올 때까지 대기
}
```

**배치 애플리케이션**
```kotlin
@SpringBootApplication
class BatchApp

fun main(args: Array<String>) {
    val context = SpringApplication.run(BatchApp::class.java, *args)
    // 배치 Job 실행
    // 완료되면 프로그램 종료
    exitProcess(SpringApplication.exit(context))
}
```

### 차이점
- **웹**: 항상 떠있으며 요청 대기
- **배치**: 실행 → 작업 → 종료

---

## 실행 방식 비교

### 1. One-time 방식 (일반적, 권장)

**개념**: 실행할 때만 프로세스 생성, 끝나면 종료

```
평소: 아무것도 안 떠있음 (리소스 0)
       ↓
Jenkins 트리거 (스케줄 or 수동)
       ↓
프로세스 시작 (java -jar batch.jar)
       ↓
Spring Boot 기동 (10-20초)
       ↓
배치 Job 실행 (10분)
       ↓
프로세스 종료
       ↓
다시 아무것도 없음
```

**Jenkins 설정**
```groovy
// Jenkinsfile
pipeline {
    agent any
    
    // 스케줄 설정
    triggers {
        cron('0 2 * * *')  // 매일 새벽 2시
    }
    
    stages {
        stage('배치 실행') {
            steps {
                sh '''
                    java -jar /app/batch.jar \
                    --spring.batch.job.names=settlement \
                    --date=2025-10-05
                '''
            }
        }
    }
    
    // 실패 시 알림
    post {
        failure {
            slackSend(message: "정산 배치 실패!")
        }
    }
}
```

**장점**
- 리소스 효율적 (실행 시에만 사용)
- 깨끗한 환경 (매번 새로 시작)
- 장애 격리 (한 배치 죽어도 다른 거 영향 없음)

**단점**
- 기동 시간 필요 (10-20초)
- 매번 Spring 컨텍스트 로딩

---

### 2. Long-running 방식

**개념**: 배치 서버를 항상 띄워놓고, REST API로 트리거

```
배치 서버 (항상 실행 중, 8080 포트)
    ↑
    | HTTP POST
    |
Jenkins --매일 새벽 2시--> curl 호출
```

**배치 서버 코드**
```kotlin
@RestController
@RequestMapping("/api/batch")
class BatchController(
    private val jobLauncher: JobLauncher,
    private val settlementJob: Job
) {
    
    @PostMapping("/trigger/settlement")
    fun runSettlement(@RequestParam date: String): String {
        
        val params = JobParametersBuilder()
            .addString("date", date)
            .addLong("time", System.currentTimeMillis())
            .toJobParameters()
        
        val execution = jobLauncher.run(settlementJob, params)
        
        return "실행 완료: ${execution.status}"
    }
}

// 서버는 항상 떠있음 (웹 서버처럼)
```

**Jenkins 설정**
```groovy
pipeline {
    triggers {
        cron('0 2 * * *')
    }
    
    stages {
        stage('배치 트리거') {
            steps {
                sh '''
                    curl -X POST \
                    http://batch-server:8080/api/batch/trigger/settlement \
                    -d "date=2025-10-05"
                '''
            }
        }
    }
}
```

**장점**
- 즉시 실행 (기동 시간 0초)
- Job 간 상태 공유 가능

**단점**
- 서버 항상 떠있어야 함 (리소스 낭비)
- 서버 죽으면 모든 배치 중단

---

## 실전 시나리오

### 시나리오 1: 간단한 스케줄 배치

**요구사항**: 매일 새벽 2시에 정산 배치

```groovy
// Jenkinsfile
pipeline {
    agent any
    triggers {
        cron('0 2 * * *')
    }
    stages {
        stage('실행') {
            steps {
                sh 'java -jar batch.jar --job.name=settlement'
            }
        }
    }
}
```

---

### 시나리오 2: 파라미터 받는 수동 실행

**요구사항**: 특정 날짜 데이터 재처리

```groovy
pipeline {
    agent any
    
    // UI에 입력 필드 생성
    parameters {
        string(name: 'TARGET_DATE', defaultValue: '2025-10-05')
        choice(name: 'JOB_TYPE', choices: ['settlement', 'report'])
    }
    
    stages {
        stage('실행') {
            steps {
                sh """
                    java -jar batch.jar \
                    --job.name=${params.JOB_TYPE} \
                    --date=${params.TARGET_DATE}
                """
            }
        }
    }
}
```

**사용법**
1. Jenkins 웹에서 "Build with Parameters" 클릭
2. 날짜 입력: 2025-09-15
3. Job 선택: settlement
4. 실행 버튼 클릭

---

### 시나리오 3: Docker 컨테이너로 실행

**요구사항**: 격리된 환경에서 실행

```groovy
pipeline {
    triggers {
        cron('0 2 * * *')
    }
    
    stages {
        stage('실행') {
            steps {
                sh '''
                    docker run --rm \
                    -e SPRING_PROFILES_ACTIVE=prod \
                    -e DB_URL=jdbc:mysql://prod-db:3306/app \
                    my-registry/batch:latest \
                    --job.name=settlement
                '''
            }
        }
    }
}
```

**흐름**
1. Jenkins 트리거
2. Docker 컨테이너 생성
3. 컨테이너 안에서 배치 실행
4. 완료 후 컨테이너 자동 삭제 (`--rm`)

---

### 시나리오 4: Kubernetes Job

**요구사항**: K8s 클러스터에서 실행

```groovy
pipeline {
    stages {
        stage('K8s Job 생성') {
            steps {
                sh '''
                    kubectl create job settlement-${BUILD_NUMBER} \
                    --image=ecr/batch:latest \
                    -- --job.name=settlement
                '''
            }
        }
        
        stage('완료 대기') {
            steps {
                sh '''
                    kubectl wait --for=condition=complete \
                    job/settlement-${BUILD_NUMBER} \
                    --timeout=30m
                '''
            }
        }
    }
}
```

---

### 시나리오 5: ECS Task 실행

**요구사항**: AWS ECS에서 실행

```groovy
pipeline {
    stages {
        stage('ECS Task 실행') {
            steps {
                sh '''
                    aws ecs run-task \
                    --cluster batch-cluster \
                    --task-definition batch-task:5 \
                    --launch-type FARGATE \
                    --overrides '{
                        "containerOverrides": [{
                            "name": "batch",
                            "command": ["--job.name=settlement"]
                        }]
                    }'
                '''
            }
        }
    }
}
```

---

## Quartz vs Jenkins

### Quartz란?

**Java 기반 스케줄링 라이브러리**
- 애플리케이션 **내부**에 포함되는 라이브러리
- Spring Boot에 통합 가능 (`spring-boot-starter-quartz`)
- 코드로 스케줄 정의

```kotlin
@Configuration
class QuartzConfig {
    
    @Bean
    fun settlementJobDetail(): JobDetail {
        return JobBuilder.newJob(SettlementJob::class.java)
            .withIdentity("settlementJob")
            .storeDurably()
            .build()
    }
    
    @Bean
    fun settlementTrigger(): Trigger {
        return TriggerBuilder.newTrigger()
            .forJob(settlementJobDetail())
            .withIdentity("settlementTrigger")
            .withSchedule(
                CronScheduleBuilder.cronSchedule("0 2 * * * ?")
            )
            .build()
    }
}
```

---

### 핵심 차이

#### 위치

**Quartz**: 애플리케이션 **안**
```
┌─────────────────────────┐
│   Spring Boot App       │
│  ┌──────────────────┐   │
│  │  Quartz          │   │  ← 내장
│  │  - 스케줄러      │   │
│  │  - Job 실행      │   │
│  └──────────────────┘   │
└─────────────────────────┘
```

**Jenkins**: 애플리케이션 **밖**
```
┌──────────────┐        ┌──────────────────┐
│   Jenkins    │──실행──→│  Batch App       │
│  - 스케줄러  │        │  - 배치 로직     │
└──────────────┘        └──────────────────┘
```

---

### 상세 비교표

| 항목 | Quartz | Jenkins |
|------|--------|---------|
| **위치** | 애플리케이션 내장 | 외부 독립 서버 |
| **배포** | 앱과 함께 배포 | 별도 설치 필요 |
| **스케줄 변경** | 코드 수정 → 재배포 | 웹 UI에서 즉시 변경 |
| **수동 실행** | API 호출 필요 | 버튼 클릭 |
| **이력 관리** | 직접 구현 | 자동 제공 |
| **알림** | 직접 구현 | 플러그인 제공 |
| **GUI** | 없음 (Admin UI 별도 구축) | 기본 제공 |
| **리소스** | 앱과 공유 | 독립적 |

---

### Quartz 클러스터링의 복잡성

#### 문제 상황

```
API 서버 3대 (로드밸런싱)

┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│  App 1      │  │  App 2      │  │  App 3      │
│  + Quartz   │  │  + Quartz   │  │  + Quartz   │
└─────────────┘  └─────────────┘  └─────────────┘

문제: 3개 다 배치 실행하면 중복 실행!
```

#### 클러스터링 구성 필요

```yaml
# application.yml
spring:
  quartz:
    job-store-type: jdbc  # 메모리 대신 DB 사용
    properties:
      org:
        quartz:
          jobStore:
            class: org.quartz.impl.jdbcjobstore.JobStoreTX
            driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
            tablePrefix: QRTZ_
            isClustered: true  # 클러스터 모드
            clusterCheckinInterval: 20000
          scheduler:
            instanceId: AUTO
```

#### DB 테이블 11개 필요

```sql
QRTZ_JOB_DETAILS
QRTZ_TRIGGERS
QRTZ_SIMPLE_TRIGGERS
QRTZ_CRON_TRIGGERS
QRTZ_BLOB_TRIGGERS
QRTZ_CALENDARS
QRTZ_PAUSED_TRIGGER_GRPS
QRTZ_FIRED_TRIGGERS
QRTZ_SCHEDULER_STATE  -- 서버 간 동기화용
QRTZ_LOCKS           -- 락 관리
QRTZ_SIMPROP_TRIGGERS
```

#### 동작 방식 (DB 락 기반)

```
새벽 2시 도착

App 1: SELECT * FROM QRTZ_LOCKS FOR UPDATE
       → 락 획득! 배치 실행

App 2: SELECT * FROM QRTZ_LOCKS FOR UPDATE
       → 대기... (App 1이 끝날 때까지)

App 3: SELECT * FROM QRTZ_LOCKS FOR UPDATE
       → 대기...

App 1: 배치 완료 → COMMIT (락 해제)
App 2: 이미 실행됐는지 체크 → SKIP
App 3: 이미 실행됐는지 체크 → SKIP
```

**문제점**
- DB 락 경합
- 불필요한 대기
- DB 부하 증가
- 복잡도 상승

---

### Jenkins는 단순

```
┌──────────────┐
│  Jenkins     │ (단일 서버, 스케줄러 1개)
└──────┬───────┘
       │
       │ 새벽 2시 트리거
       │
       └──→ K8s Job 생성 (새 Pod)
            → 배치 실행 → 종료
```

**특징**
- Jenkins 자체는 1대 (클러스터링 불필요)
- 배치 실행은 매번 새로운 환경
- 동기화 문제 없음
- DB 락 불필요

---

### 리소스 낭비 비교

#### Quartz 클러스터

```
API 서버 3대 (각 2GB 메모리)

┌─────────────────┐
│ App 1           │
│ - API (1GB)     │
│ - Quartz (500MB)│ ← 항상 떠있음
└─────────────────┘

┌─────────────────┐
│ App 2           │
│ - API (1GB)     │
│ - Quartz (500MB)│ ← 항상 떠있음
└─────────────────┘

┌─────────────────┐
│ App 3           │
│ - API (1GB)     │
│ - Quartz (500MB)│ ← 항상 떠있음
└─────────────────┘

총 리소스: 4.5GB
실제 사용: 1개만 배치 실행, 2개는 대기만
```

#### Jenkins

```
┌─────────────────┐
│ API 서버 3대    │
│ - API (1GB)     │ ← Quartz 없음
│ - API (1GB)     │
│ - API (1GB)     │
└─────────────────┘

┌─────────────────┐
│ Jenkins (512MB) │
└─────────────────┘

┌─────────────────┐
│ K8s Job         │ ← 실행 시에만 생성
│ - 배치 (2GB)    │
└─────────────────┘
    ↓ 완료 후 삭제

평소: 3.5GB
실행 시: 5.5GB
```

---

### 복잡도 비교

#### Quartz - 직접 구현 필요

```kotlin
// 1. 실행 이력 저장
@Entity
class JobExecutionHistory(...)

@Component
class JobHistoryListener : JobListenerSupport() {
    override fun jobWasExecuted(...) {
        // 직접 저장
    }
}

// 2. 수동 실행 API
@RestController
class BatchController {
    @PostMapping("/batch/trigger")
    fun trigger() { ... }
}

// 3. 실행 이력 조회 API
@GetMapping("/batch/history")
fun getHistory() { ... }

// 4. 알림
@Component
class JobFailureListener {
    override fun jobWasExecuted(...) {
        if (exception != null) {
            slackService.send("실패!")
        }
    }
}

// 5. 클러스터 모니터링
@Component
class ClusterMonitor {
    @Scheduled(fixedDelay = 60000)
    fun checkHealth() { ... }
}
```

#### Jenkins - 설정만

```groovy
pipeline {
    triggers { cron('0 2 * * *') }
    parameters {
        string(name: 'DATE')
    }
    stages {
        stage('실행') {
            steps {
                sh "kubectl create job settlement"
            }
        }
    }
    post {
        failure {
            slackSend(message: "실패!")
        }
    }
}
```

---

### 언제 뭘 쓰나?

#### Quartz 적합

✅ **단순한 스케줄링만 필요**
- 배치 1-2개
- 스케줄 변경 거의 없음
- 별도 인프라 구축 부담

✅ **앱 내부 통합이 중요**
```kotlin
// 주문 처리 후 바로 스케줄링
fun createOrder(order: Order) {
    orderRepository.save(order)
    
    // 30분 후 자동 취소 스케줄 등록
    scheduler.scheduleJob(
        CancelOrderJob(order.id),
        Date(System.currentTimeMillis() + 1800000)
    )
}
```

#### Jenkins 적합

✅ **여러 배치 중앙 관리**
- 배치 5개 이상
- 다양한 스케줄

✅ **운영 편의성 중요**
- 비개발자도 실행/확인 필요
- 파라미터 자주 변경
- 수동 재실행 빈번

✅ **MSA/클라우드 환경**
- K8s Job/ECS Task 실행

✅ **이력/모니터링 중요**
- 실행 이력 추적
- 실패 알림
- 성능 모니터링

---

## 결론 및 권장사항

### Spring Boot + K8s/ECS 환경

**→ Jenkins 권장**

**이유**
- 배치 격리 (One-time 방식)
- 중앙 관리 용이
- K8s/ECS와 통합 간편
- 운영 편의성 (GUI, 이력, 알림)

### Quartz 사용 시나리오

**앱 내부 간단한 스케줄링용으로만**
- 캐시 갱신
- 임시 작업 스케줄

### 실무 권장 구조

```
[코드 변경]
Git Push → Jenkins Build Job → Docker Image → ECR

[배치 실행]
Jenkins Run Job (cron) → ECR 이미지 pull → 컨테이너 실행 → 종료
```

**분리 이유**
- 빌드: 코드 변경 시에만
- 실행: 매일 또는 수시
- 빌드 실패해도 기존 이미지로 실행 가능

---

## 핵심 요약

### Jenkins
- 배치를 **언제, 어떻게** 실행할지 관리하는 도구
- 스케줄러 + 실행 버튼 + 모니터링 역할

### One-time 방식 (권장)
- 실행 시에만 프로세스 생성 → 완료 후 종료
- 리소스 효율적
- MSA/클라우드 환경에 적합

### Long-running 방식
- 서버 항상 실행 → REST API로 트리거
- 즉시 실행 필요 시 사용

### Quartz vs Jenkins
- **Quartz**: 클러스터 환경에서 매우 복잡 (DB 락, 11개 테이블, 동기화)
- **Jenkins**: 단순하고 안정적 (스케줄러 1개, 배치 격리)

---

*작성일: 2025-10-05*
*대상: Spring Boot + Kotlin + K8s/ECS 환경*
