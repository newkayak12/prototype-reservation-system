# Kafka Parallel Consumer
## 📕 1. 정의
> - Kafka Consumer의 **순차 처리 한계**를 극복하는 라이브러리
> - **Key별 순서 보장**과 **병렬 처리**를 동시에 제공
> - **복잡한 오프셋 관리**를 자동화하여 메시지 유실 방지

## 🎯 2. 목적
1. **처리량 향상**: 기존 순차 처리 대비 **10배** 성능 향상
2. **순서 보장**: 동일 Key(timeTableId)의 이벤트는 순서대로 처리
3. **시스템 안정성**: 메시지 유실 없는 안전한 오프셋 관리
4. **리소스 효율성**: 최적의 동시성으로 CPU 활용률 극대화

## 📜 3. 핵심 특성
### ✅ 특성
1. **Key 기반 분산**: 동일 Key는 항상 같은 Worker Thread에서 처리
2. **병렬 처리**: 서로 다른 Key의 메시지는 동시 처리 가능
3. **자동 오프셋 관리**: Out-of-order 완료에도 안전한 커밋 보장
4. **백프레셀 제어**: 느린 Consumer가 전체 시스템을 지연시키지 않음
5. **실패 처리**: 재시도 + DLQ 조합으로 안정성 확보
6. **모니터링**: 풍부한 메트릭과 상태 추적 기능

### ❌ 주의사항
1. **복잡성 증가**: 기존 @KafkaListener 대비 설정과 운영 복잡
2. **메모리 사용량**: Worker Pool과 메시지 버퍼로 인한 메모리 증가
3. **디버깅 어려움**: 병렬 처리로 인한 로깅과 트레이싱 복잡성
4. **의존성 추가**: 외부 라이브러리 추가로 인한 호환성 고려 필요

### ↔️ 추가 사항
1. **Spring Kafka와의 차이점**

|항목|Spring @KafkaListener|Parallel Consumer|
|:---:|:---:|:---:|
|**처리 방식**|파티션별 순차 처리|Key별 순서 보장 + 병렬 처리|
|**동시성**|파티션 수 = 최대 동시성|설정 가능한 Worker Pool|
|**순서 보장**|파티션 내 완전 순서|Key 기준 순서 보장|
|**오프셋 관리**|단순 순차 커밋|복잡한 Out-of-order 추적|
|**성능**|저처리량|고처리량|
|**복잡도**|낮음|높음|

## ⚙️ 4. 설정

### 4.1 의존성 추가
```kotlin
// build.gradle.kts
dependencies {
    implementation("io.confluent.parallelconsumer:parallel-consumer-core:0.5.2.13")
    implementation("org.springframework.kafka:spring-kafka")
}
```

### 4.2 Kafka 설정
```yaml
# application.yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
    consumer:
      group-id: reservation-service
      auto-offset-reset: earliest
      enable-auto-commit: false  # 수동 커밋으로 정확성 보장
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        isolation.level: read_committed  # 트랜잭션 메시지만 읽기
        max.poll.records: 500
      parallel:
        max-concurrency: 16           # 최대 Worker 스레드 수
        processing-order: key         # Key 기반 순서 보장
        commit-interval: 5000         # 5초마다 오프셋 커밋
```

### 4.3 Parallel Consumer 빈 설정
```kotlin
@Configuration
@EnableKafka
class ParallelConsumerConfig {

    @Bean
    fun parallelConsumer(
        kafkaProperties: KafkaProperties
    ): ParallelStreamProcessor<String, TimeTableOccupiedEvent> {
        
        val consumerProps = kafkaProperties.consumer.buildProperties()
        
        val options = ParallelConsumerOptions.builder<String, TimeTableOccupiedEvent>()
            .consumer(KafkaConsumer(consumerProps))
            .ordering(ProcessingOrder.KEY)                      // Key 기반 순서 보장
            .maxConcurrency(16)                                 // 최대 동시 처리 수
            .commitMode(CommitMode.PERIODIC_TRANSACTIONAL)      // 안전한 오프셋 관리
            .commitInterval(Duration.ofSeconds(5))              // 5초마다 커밋
            .retryDelayProvider { _, _ -> Duration.ofSeconds(10) } // 실패 시 10초 대기
            .build()

        return ParallelStreamProcessor.createEosStreamProcessor(options)
    }
}
```

### 4.4 Consumer 구현
```kotlin
@Component
class TimeTableOccupancyKafkaListener(
    private val parallelConsumer: ParallelStreamProcessor<String, TimeTableOccupiedEvent>,
    private val httpInterface: FindTimeTableOccupancyHttpInterface,
    private val createReservationUseCase: CreateReservationUseCase,
) {
    private val log = loggerFactory<TimeTableOccupancyKafkaListener>()

    @PostConstruct
    fun startConsuming() {
        parallelConsumer.subscribe(listOf("time-table-occupied")) { consumerRecord ->
            
            val event = consumerRecord.value()
            val key = "${event.timeTableId}_${event.timeTableOccupancyId}"  // 고유 Key 생성
            
            log.info("Processing event: $key by thread: ${Thread.currentThread().name}")
            
            try {
                processEvent(event)
                log.info("Successfully processed: $key")
            } catch (exception: Exception) {
                log.error("Failed to process: $key", exception)
                throw exception  // Parallel Consumer가 재시도 처리
            }
        }
    }
    
    private fun processEvent(event: TimeTableOccupiedEvent) {
        // HTTP Interface 호출로 데이터 조회
        val responseEntity = httpInterface.findTimeTableOccupancyInternally(
            timeTableId = event.timeTableId,
            timeTableOccupancyId = event.timeTableOccupancyId,
        )
        
        // 예약 생성
        responseEntity.body?.let { body ->
            val command = createReservationCommand(body)
            createReservationUseCase.execute(command)
        } ?: throw ResponseBodyRequiredException()
    }
    
    @PreDestroy
    fun shutdown() {
        parallelConsumer.close()  // 우아한 종료
    }
}
```

## 💥 5. TroubleShooting

### 5.1 성능 튜닝 과정
- **목표**: 기존 @KafkaListener의 20 msg/sec → 200 msg/sec 달성
- **제약사항**: HTTP Interface 호출로 인한 I/O 지연 (평균 50ms/메시지)

#### 1단계: 보수적 시작 (max-concurrency: 4)
- **결과**: 20 → 60 msg/sec (3배 향상)
- **에러율**: 0%
- **시스템 안정성**: 양호

#### 2단계: 점진적 증가 (max-concurrency: 8) 
- **결과**: 60 → 120 msg/sec (6배 향상)
- **HTTP Interface 응답시간**: 평균 15ms (Redis 캐시 효과)
- **MySQL 커넥션 사용률**: 60%

#### 3단계: 최적점 발견 (max-concurrency: 16)
```yaml
# 최종 설정
parallel:
  max-concurrency: 16
  processing-order: key
  commit-mode: periodic_transactional
  commit-interval: 5000
  retry-delay: 10000
```

- **최종 성능**: 20 → 200 msg/sec (**10배 향상**)
- **CPU 사용률**: 15% → 70% (리소스 효율적 활용)
- **메모리 사용률**: 안정적 유지
- **에러율**: < 0.1%

### 5.2 주요 이슈와 해결책

#### 문제 1: 순서 꼬임으로 인한 중복 예약
```kotlin
// 문제 상황
테이블_A 점유 이벤트_1 → 스레드_1에서 처리 중 (HTTP 호출 지연)
테이블_A 점유 이벤트_2 → 🚨 스레드_2에서 처리됨! (순서 역전)
```

**원인**: Key 생성 시 고유성 부족
```kotlin
// 잘못된 Key 생성
override fun key(): String = timeTableId  // ❌ 불충분!

// 올바른 Key 생성  
override fun key(): String = "${timeTableId}_${timeTableOccupancyId}"  // ✅ 완전한 고유성
```

**해결**: Key에 `occupancyId` 포함하여 독립적 처리 보장

#### 문제 2: 과도한 동시성으로 인한 시스템 과부하
```yaml
# 실패한 설정
parallel:
  max-concurrency: 50    # 🔥 너무 과격함
```

**결과**:
```
15:30:00 - Consumer 시작
15:30:05 - HTTP Interface 서버 과부하 (500 에러 폭증)
15:30:08 - MySQL 커넥션 풀 고갈 (100/100 사용 중)
15:30:10 - Redis 연결 타임아웃
15:30:15 - 전체 시스템 다운 😱
```

**해결**: 
- **단계적 튜닝**: 4 → 8 → 16으로 점진적 증가
- **시스템 모니터링**: 각 단계마다 리소스 사용량 확인
- **백프레셀**: 다운스트림 시스템 한계 고려

#### 문제 3: 복잡한 오프셋 커밋 지연
```kotlin
// 문제 상황
Offset 100: 완료 ✅
Offset 101: 완료 ✅  
Offset 102: 처리 중... ⏳ (느린 메시지)
Offset 103: 완료 ✅
Offset 104: 완료 ✅

// 🚨 Offset 102가 완료될 때까지 103, 104 커밋 불가
```

**해결**: PERIODIC_TRANSACTIONAL 모드 + 타임아웃 설정
```kotlin
ParallelConsumerOptions.builder()
    .commitMode(CommitMode.PERIODIC_TRANSACTIONAL)
    .commitInterval(Duration.ofSeconds(5))     // 주기적 커밋
    .maxMessagesWaitingBetweenCommits(1000)    // 최대 대기 메시지 수
    .build()
```

### 5.3 모니터링과 알람
```kotlin
# 핵심 메트릭
- parallel_consumer_messages_processed_total: 총 처리 메시지 수
- parallel_consumer_active_workers: 현재 활성 Worker 수
- parallel_consumer_queued_messages: 처리 대기 메시지 수
- parallel_consumer_processing_duration: 평균 처리 시간
- parallel_consumer_offset_lag: 미커밋 오프셋 지연
```

**Grafana 알람 설정**:
```yaml
- alert: ParallelConsumerHighLag
  expr: parallel_consumer_offset_lag > 1000
  for: 5m
  
- alert: ParallelConsumerLowThroughput  
  expr: rate(parallel_consumer_messages_processed_total[5m]) < 50
  for: 10m
```

## 🔍 6. 결론
Parallel Consumer는 **Kafka 순차 처리의 근본적 한계**를 극복하는 강력한 도구입니다. 
특히 **I/O 집약적 작업**(HTTP 호출, DB 쿼리)이 포함된 Consumer에서 **극적인 성능 향상**을 제공합니다.

**핵심 장점**:
- **10배 처리량 향상**: 20 → 200 msg/sec
- **순서 보장**: Key 기반으로 비즈니스 로직 안정성 확보  
- **안전한 오프셋 관리**: 메시지 유실 없는 완벽한 추적

**도입 고려사항**:
- **복잡성**: 설정과 운영 난이도 증가
- **리소스**: 메모리 사용량 증가 및 시스템 부하 고려
- **튜닝**: 최적 동시성 찾기 위한 단계적 접근 필요

**권장 사용 시나리오**:
✅ **I/O 지연이 큰 Consumer**
✅ **높은 처리량이 필요한 상황**  
✅ **Key별 순서 보장이 중요한 비즈니스 로직**
❌ **단순하고 빠른 처리만 필요한 경우**
❌ **시스템 복잡성을 피하고 싶은 경우**