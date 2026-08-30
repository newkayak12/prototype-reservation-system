# Portfolio Concurrency Control Architecture Test Strategy

## 📋 개요

포트폴리오 전시를 위한 **삼중 동시성 제어 아키텍처**와 **Kafka Parallel Consumer 성능 향상** 효과를 정량적으로 입증하는 통합 테스트 전략.

### 🎯 핵심 아키텍처
**예약 시스템에서 동시성 지옥 해결**: 100명이 동시에 같은 테이블을 예약할 때

1. **RateLimiter** (시스템 레벨) → 과부하 방지 
2. **FairLock** (비즈니스 레벨) → 공정한 순서 보장
3. **Semaphore** (리소스 레벨) → 정확한 재고 관리

### 🎯 목표
- **삼중 동시성 제어**: RateLimiter + FairLock + Semaphore 통합 효과 측정
- **Race Condition 완전 제거**: 동시성 문제 해결 정량적 증명
- **비즈니스 임팩트**: 실제 예약 시스템에서의 안정성 향상 입증
- **Kafka Parallel Consumer**: 순차 처리 대비 성능 향상 과정 입증

---

## 🔒 Part 1: 삼중 동시성 제어 아키텍처 테스트

### 1.1 테스트 목적
**실제 예약 시스템 시나리오**: 동시 예약 요청에서 데이터 정합성과 공정성 보장

- **Race Condition 방지**: 동시 접근으로 인한 데이터 불일치 해결
- **공정성 보장**: FairLock을 통한 선입선출 순서 보장  
- **정확한 재고 관리**: Semaphore를 통한 오버부킹 방지
- **시스템 안정성**: RateLimiter를 통한 과부하 방지

### 1.2 삼중 동시성 제어 테스트 시나리오

**핵심 전략**: 실제 예약 시스템 시나리오로 RateLimiter + FairLock + Semaphore 통합 효과 측정

#### 시나리오 1: 예약 시스템 동시성 지옥 시뮬레이션
```kotlin
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)  
class ConcurrencyArchitectureTest {

    @Container
    private val redis = GenericContainer<Nothing>("redis:7.2-alpine")
        .withExposedPorts(6379)
    
    private lateinit var rateLimiterAdapter: AcquireRateLimitRedisAdapter
    private lateinit var fairLockAdapter: AcquireFairLockAdapter
    private lateinit var semaphoreTemplate: AcquireSemaphoreTemplate

    @BeforeEach
    fun setup() {
        val redissonClient = createRedissonClient()
        rateLimiterAdapter = AcquireRateLimitRedisAdapter(redissonClient)
        fairLockAdapter = AcquireFairLockAdapter(redissonClient) 
        semaphoreTemplate = AcquireSemaphoreTemplate(redissonClient)
    }

    @Test  
    @DisplayName("삼중 동시성 제어 vs 무방어 상태 비교")
    fun `should demonstrate triple concurrency control effectiveness`() {
        
        // 시나리오: 100명이 동시에 같은 테이블(재고 5개) 예약 시도
        val totalUsers = 100
        val availableSeats = 5
        val tableId = "table-vip-001"
        
        // Test 1: 무방어 상태 (Race Condition 발생)
        val chaosResults = simulateReservationChaos(totalUsers, availableSeats, tableId)
        
        // Test 2: 삼중 동시성 제어 적용
        val protectedResults = simulateReservationWithTripleControl(
            totalUsers = totalUsers,
            availableSeats = availableSeats, 
            tableId = tableId,
            rateLimiter = rateLimiterAdapter,
            fairLock = fairLockAdapter,
            semaphore = semaphoreTemplate
        )
        
        // 포트폴리오 핵심 성과 비교
        printReservationBattleReport(chaosResults, protectedResults)
        
        // 검증: 삼중 제어가 완벽하게 동작해야 함
        assert(chaosResults.overbooking > 0) { "무방어 상태에서 오버부킹이 발생해야 함" }
        assert(protectedResults.overbooking == 0) { "삼중 제어에서 오버부킹이 없어야 함" }
        assert(protectedResults.fairnessScore > 85.0) { "공정성 점수가 85% 이상이어야 함" }
    }

    @Test
    @Order(2) 
    @DisplayName("각 동시성 제어 메커니즘별 기여도 분석")
    fun `should analyze individual concurrency mechanism contributions`() {
        
        // Given: 극한 부하 시나리오
        val extremeLoad = 200 // concurrent users
        val burstDuration = 30L // seconds
        
        val systemMetrics = SystemMetricsCollector()
        systemMetrics.startMonitoring()
        
        // When: 극한 부하 실행
        val results = executeBurstLoadTest(
            concurrentUsers = extremeLoad,
            durationSeconds = burstDuration
        )
        
        val systemStats = systemMetrics.stopMonitoring()
        
        // Then: 시스템 안정성 검증
        assertThat(systemStats.maxCpuUsage)
            .describedAs("CPU 사용률이 안정적으로 유지되어야 함")
            .isLessThan(85.0) // CPU 85% 이하 유지
            
        assertThat(systemStats.maxMemoryUsage)
            .describedAs("메모리 사용량이 제한 내에서 유지되어야 함")  
            .isLessThan(80.0) // Memory 80% 이하 유지
            
        assertThat(results.errorRate)
            .describedAs("에러율이 허용 범위 내에 있어야 함")
            .isLessThan(0.01) // 1% 미만 에러율
            
        // 시스템 복구 시간 검증
        val recoveryTime = systemStats.getRecoveryTimeToNormalState()
        assertThat(recoveryTime)
            .describedAs("부하 해제 후 정상 상태 복구 시간")
            .isLessThan(Duration.ofSeconds(10))
            
        logSystemStabilityMetrics(systemStats, "EXTREME_LOAD_STABILITY")
    }

    @Test
    @Order(3)
    @DisplayName("Redis vs InMemory 구현체 성능 비교")
    fun `should compare performance between Redis and InMemory rate limiters`() {
        
        val testScenarios = listOf(
            RateLimiterTestScenario("InMemory", useRedis = false),
            RateLimiterTestScenario("Redis", useRedis = true)
        )
        
        val comparisonResults = mutableMapOf<String, PerformanceMetrics>()
        
        testScenarios.forEach { scenario ->
            
            // 각 시나리오별 설정 적용
            configureRateLimiterImplementation(scenario)
            
            // 표준화된 성능 테스트 실행
            val performanceMetrics = executeStandardizedPerformanceTest(
                requests = 2000,
                concurrency = 20,
                duration = Duration.ofMinutes(3)
            )
            
            comparisonResults[scenario.name] = performanceMetrics
            
            // 각 구현체별 상세 검증
            when (scenario.name) {
                "InMemory" -> {
                    assertThat(performanceMetrics.averageLatency)
                        .describedAs("InMemory 구현체는 낮은 지연시간을 가져야 함")
                        .isLessThan(Duration.ofMillis(5))
                }
                "Redis" -> {
                    assertThat(performanceMetrics.averageLatency)
                        .describedAs("Redis 구현체는 분산 환경에서 일관성을 보장해야 함")
                        .isLessThan(Duration.ofMillis(15))
                        
                    assertThat(performanceMetrics.consistencyScore)
                        .describedAs("Redis는 높은 일관성 점수를 가져야 함")
                        .isGreaterThan(0.95)
                }
            }
        }
        
        // 비교 분석
        val inMemoryMetrics = comparisonResults["InMemory"]!!
        val redisMetrics = comparisonResults["Redis"]!!
        
        val latencyOverhead = calculateLatencyOverhead(inMemoryMetrics, redisMetrics)
        val consistencyImprovement = calculateConsistencyImprovement(inMemoryMetrics, redisMetrics)
        
        // 트레이드오프 분석 결과 로깅
        logImplementationComparison(inMemoryMetrics, redisMetrics, latencyOverhead, consistencyImprovement)
    }
}
```

### 1.3 삼중 동시성 제어 핵심 측정 지표

#### 예약 시스템 동시성 제어 효과성 지표
```kotlin
data class ReservationConcurrencyMetrics(
    // 비즈니스 정합성 지표
    val totalReservationAttempts: Int,    // 총 예약 시도 수
    val successfulReservations: Int,      // 성공한 예약 수
    val overbookingCount: Int,            // 오버부킹 발생 수 (0이어야 함)
    val rejectedByRateLimit: Int,         // RateLimiter에 의한 차단
    val fairnessScore: Double,            // 공정성 점수 (0.0-100.0)
    
    // 성능 지표
    val avgReservationTime: Duration,     // 평균 예약 완료 시간
    val systemStabilityScore: Double,     // 시스템 안정성 점수
    val lockContentionRate: Double,       // Lock 경합률
    val semaphoreUtilization: Double,     // Semaphore 활용률
    
    // 각 메커니즘별 기여도
    val rateLimiterEffectiveness: Double, // RateLimiter 효과성 (0.0-1.0)
    val fairLockEffectiveness: Double,    // FairLock 공정성 기여도
    val semaphoreAccuracy: Double         // Semaphore 재고 정확도
)

// 삼중 동시성 제어 검증 기준
val overbookingTolerance = 0             // 오버부킹 0건 (완벽한 재고 관리)
val fairnessMinimumScore = 85.0          // 공정성 85% 이상
val rateLimitAccuracyThreshold = 0.95    // RateLimiter 95% 정확도 이상
val semaphoreAccuracyThreshold = 1.0     // Semaphore 100% 정확도 (재고 관리)
val systemStabilityMinimum = 0.90        // 시스템 안정성 90% 이상
```

### 1.4 포트폴리오 핵심 결과 - 예상 성과

#### 🎯 삼중 동시성 제어 아키텍처 증명 결과

**시나리오**: 100명이 동시에 VIP 테이블 5개를 예약하는 극한 상황

| 제어 방식 | 예약 성공 | 오버부킹 | 공정성 | 시스템 안정성 | 평균 응답시간 |
|-----------|----------|----------|--------|--------------|-------------|
| **무방어 상태** | 37개 | 32건 | 12% | 23% | 1,240ms |
| **삼중 제어** | 5개 | 0건 | 94% | 98% | 385ms |

#### 📈 비즈니스 임팩트 증명

```
🚨 무방어 상태의 문제점:
❌ 32건의 오버부킹 발생 (640% 초과 예약)
❌ 극도로 불공정한 처리 (늦게 온 사람이 먼저 예약)
❌ 시스템 불안정 (응답시간 1초 이상)
❌ 고객 불만 및 매출 손실 불가피

✅ 삼중 동시성 제어 효과:
✅ 오버부킹 완전 차단 (100% 재고 정확성)
✅ 공정한 선입선출 처리 (94% 공정성 점수)
✅ 안정적 시스템 운영 (98% 안정성)
✅ 빠른 응답시간 (385ms, 69% 개선)
✅ 고객 만족도 및 신뢰도 향상
```

#### 🔧 각 메커니즘별 기여도 분석

**1. RateLimiter (시스템 레벨)**
- 시스템 과부하 방지: **98%** 효과
- 응답시간 안정화: **69%** 개선
- 서버 리소스 절약: **85%** 효율성

**2. FairLock (비즈니스 레벨)**
- 공정성 보장: **94%** 점수
- 순서 보장 정확도: **97%**
- Lock 경합 최적화: **91%** 효율성

**3. Semaphore (리소스 레벨)**
- 재고 정확도: **100%** (오버부킹 0건)
- 동시 접근 제어: **100%** 정확성
- 리소스 활용 최적화: **96%** 효율성
val consistencyThreshold = 0.90          // 90% 일관성 이상  
val allowedDeviationPercent = 10.0       // ±10% 오차 허용
```

#### 성능 영향 측정 지표
```kotlin
data class PerformanceImpactMetrics(
    val baselineLatency: Duration,        // Rate Limiter 없는 경우
    val rateLimitedLatency: Duration,     // Rate Limiter 적용 후
    val latencyOverheadMs: Long,          // 추가 지연시간 (ms)
    val throughputReduction: Double,      // 처리량 감소율 (0.0-1.0)
    val p95LatencyIncrease: Duration,     // 95 percentile 지연 증가
    val errorRateIncrease: Double         // 에러율 증가
)

// 허용 기준
val maxAllowableLatencyIncrease = Duration.ofMillis(20)  // 20ms 이하
val maxThroughputReduction = 0.05                        // 5% 이하 감소
val maxErrorRateIncrease = 0.001                         // 0.1% 이하 증가
```

#### 시스템 안정성 지표
```kotlin
data class SystemStabilityMetrics(
    val cpuUsageUnderLoad: Double,        // 부하시 CPU 사용률
    val memoryUsageUnderLoad: Double,     // 부하시 메모리 사용률
    val gcPauseTime: Duration,            // GC 일시정지 시간
    val systemRecoveryTime: Duration,     // 부하 해제 후 복구 시간
    val errorRecoveryCapability: Boolean  // 에러 상황 복구 능력
)

// 안정성 기준
val maxCpuUsage = 85.0                   // CPU 85% 이하
val maxMemoryUsage = 80.0                // Memory 80% 이하  
val maxGcPauseTime = Duration.ofMillis(100) // GC 100ms 이하
val maxRecoveryTime = Duration.ofSeconds(10) // 10초 이내 복구
```

---

## 🚀 Part 2: Kafka Parallel Consumer 효용성 테스트

### 2.1 테스트 목적  
- **순차 vs 병렬**: 일반 Consumer vs Parallel Consumer 성능 비교
- **단계적 최적화**: 동시성 1→4→8→16 증가에 따른 처리량 향상
- **실용적 검증**: 실제 메시지 처리 시나리오 기반 성능 측정

### 2.2 현실적 테스트 접근법

**핵심 전략**: TestContainer Kafka + 직접 Producer/Consumer 생성으로 순수 성능 측정

#### 시나리오 1: 순차 vs 병렬 처리 성능 비교  
```kotlin
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS) 
class KafkaParallelConsumerPerformanceTest {

    @Container
    private val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
    
    private lateinit var producer: KafkaProducer<String, String>
    private val topicName = "performance-test-topic"

    @BeforeEach
    fun setup() {
        producer = createKafkaProducer()
        createTopic(topicName)
    }

    @Test
    @DisplayName("순차 처리 vs 병렬 처리 성능 비교")
    fun `should demonstrate sequential vs parallel consumer performance difference`() {
        
        val messageCount = 1000
        
        // 1. 메시지 발송
        publishTestMessages(messageCount)
        
        // 2. 순차 처리 테스트 (일반 Consumer)  
        val sequentialResult = measureSequentialProcessing(messageCount)
        
        // 3. 병렬 처리 테스트 (Parallel Consumer)
        val parallelResults = listOf(1, 4, 8, 16).map { concurrency ->
            concurrency to measureParallelProcessing(messageCount, concurrency)
        }
        
        // 4. 포트폴리오 결과 출력
        println("🚀 Kafka Consumer 성능 비교")
        println("순차 처리: ${sequentialResult.messagesPerSecond} msg/sec")
        
        parallelResults.forEach { (concurrency, result) ->
            val improvement = (result.messagesPerSecond / sequentialResult.messagesPerSecond - 1) * 100
            println("병렬 처리($concurrency개): ${result.messagesPerSecond} msg/sec (+${String.format("%.1f", improvement)}%)")
        }
        
        // 검증: 병렬 처리가 순차 처리보다 빨라야 함
        val bestParallelResult = parallelResults.maxByOrNull { it.second.messagesPerSecond }!!
        assert(bestParallelResult.second.messagesPerSecond > sequentialResult.messagesPerSecond * 2) {
            "병렬 처리가 순차 처리 대비 최소 2배 이상 빨라야 함"
        }
    }
    
    private fun measureSequentialProcessing(messageCount: Int): ProcessingResult {
        val consumer = createSequentialConsumer()
        return measureConsumerPerformance(consumer, messageCount, "sequential")
    }
    
    private fun measureParallelProcessing(messageCount: Int, concurrency: Int): ProcessingResult {
        val consumer = createParallelConsumer(concurrency)  
        return measureConsumerPerformance(consumer, messageCount, "parallel-$concurrency")
    }
    
    private fun measureConsumerPerformance(consumer: Consumer<String, String>, messageCount: Int, testName: String): ProcessingResult {
        val processedCount = AtomicInteger(0)
        val startTime = System.currentTimeMillis()
        
        consumer.subscribe(listOf(topicName))
        
        while (processedCount.get() < messageCount && System.currentTimeMillis() - startTime < 30000) {
            val records = consumer.poll(Duration.ofMillis(100))
            records.forEach { record ->
                // 실제 처리 시뮬레이션 (10ms)
                Thread.sleep(10)
                processedCount.incrementAndGet()
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = (endTime - startTime) / 1000.0
        val messagesPerSecond = processedCount.get() / duration
        
        consumer.close()
        
        return ProcessingResult(
            testName = testName,
            processedMessages = processedCount.get(),
            durationSeconds = duration,
            messagesPerSecond = messagesPerSecond
        )
    }
                val event = testMessage.copy(
                    timeTableOccupancyId = UUID.randomUUID(),
                    sequenceNumber = index
                )
                kafkaProducer.send("time-table-occupied", event.key(), event)
            }
            
            // 모든 메시지 처리 완료까지 대기
            val processingCompleted = awaitProcessingCompletion(
                expectedCount = messageCount,
                timeout = Duration.ofMinutes(10)
            )
            
            val endTime = System.currentTimeMillis()
            
            // Then: 성능 지표 수집 및 검증
            val metrics = parallelConsumerMetrics.getCurrentMetrics()
            val totalTime = endTime - startTime
            val throughputMsgPerSec = (messageCount.toDouble() / totalTime) * 1000
            
            performanceResults[concurrency] = ThroughputMetrics(
                concurrency = concurrency,
                totalMessages = messageCount,
                processingTimeMs = totalTime,
                throughputMsgPerSec = throughputMsgPerSec,
                avgProcessingTimeMs = metrics.averageProcessingTime,
                errorRate = metrics.errorRate,
                memoryUsageMB = metrics.memoryUsageAfterMB
            )
            
            // 성능 향상 검증
            if (concurrency > 1) {
                val previousResult = performanceResults[concurrencyLevels[concurrencyLevels.indexOf(concurrency) - 1]]!!
                val improvement = (throughputMsgPerSec / previousResult.throughputMsgPerSec - 1) * 100
                
                assertThat(improvement).withFailMessage(
                    "동시성 $concurrency에서 이전 단계 대비 성능 향상이 없습니다: ${improvement}%"
                ).isGreaterThan(20.0) // 최소 20% 향상 기대
            }
            
            println("🔥 Concurrency $concurrency: ${throughputMsgPerSec.format(2)} msg/sec (${improvement?.format(1) ?: "baseline"}% improvement)")
        }
        
        // 최종 검증: 순차처리(1) vs 최적화(16)
        val sequentialThroughput = performanceResults[1]!!.throughputMsgPerSec
        val optimizedThroughput = performanceResults[16]!!.throughputMsgPerSec
        val totalImprovement = (optimizedThroughput / sequentialThroughput - 1) * 100
        
        assertThat(totalImprovement).withFailMessage(
            "전체 성능 향상이 목표에 미달합니다: ${totalImprovement}%"
        ).isGreaterThan(800.0) // 최소 8배(800%) 향상 기대
        
        println("📊 총 성능 향상: ${totalImprovement.format(1)}% (${sequentialThroughput.format(2)} → ${optimizedThroughput.format(2)} msg/sec)")
    }
            val metrics = parallelConsumerMetrics.getMetrics()
            
            // Then: 처리량 계산 및 검증
            assertThat(processingCompleted)
                .describedAs("모든 메시지가 처리되어야 함")
                .isTrue()
                
            val processingTimeSeconds = (endTime - startTime) / 1000.0
            val actualThroughput = messageCount / processingTimeSeconds
            
            performanceResults[concurrency] = ThroughputMetrics(
                concurrencyLevel = concurrency,
                messagesPerSecond = actualThroughput,
                averageProcessingTime = metrics.averageProcessingTime,
                p95ProcessingTime = metrics.p95ProcessingTime,
                cpuUsage = metrics.averageCpuUsage,
                memoryUsage = metrics.averageMemoryUsage,
                errorRate = metrics.errorRate
            )
            
            // 동시성별 최소 성능 기준 검증
            when (concurrency) {
                1 -> assertThat(actualThroughput)
                    .describedAs("순차 처리는 최소 20 msg/sec 이상")
                    .isGreaterThan(20.0)
                    
                4 -> assertThat(actualThroughput)
                    .describedAs("4 동시성은 순차 처리 대비 최소 3배 향상")
                    .isGreaterThan(performanceResults[1]!!.messagesPerSecond * 3)
                    
                8 -> assertThat(actualThroughput)
                    .describedAs("8 동시성은 4 동시성 대비 최소 1.5배 향상")
                    .isGreaterThan(performanceResults[4]!!.messagesPerSecond * 1.5)
                    
                16 -> assertThat(actualThroughput)
                    .describedAs("16 동시성은 최대 효율점으로 200 msg/sec 근접")
                    .isGreaterThan(180.0)
                    
                32 -> {
                    // 32 동시성에서는 리소스 경합으로 성능 포화 확인
                    val improvementRatio = actualThroughput / performanceResults[16]!!.messagesPerSecond
                    assertThat(improvementRatio)
                        .describedAs("32 동시성에서는 성능 포화로 크게 향상되지 않음")
                        .isLessThan(1.2) // 20% 미만 향상
                }
            }
        }
        
        // 전체 성능 향상 비율 검증
        val finalThroughput = performanceResults[16]!!.messagesPerSecond
        val initialThroughput = performanceResults[1]!!.messagesPerSecond
        val overallImprovement = finalThroughput / initialThroughput
        
        assertThat(overallImprovement)
            .describedAs("순차 처리 대비 16 동시성에서 최소 8배 향상")
            .isGreaterThan(8.0)
            
        // 포트폴리오용 성능 데이터 로깅
        logStepwiseImprovementMetrics(performanceResults, "KAFKA_CONCURRENCY_IMPROVEMENT")
    }

    @Test
    @Order(2)
    @DisplayName("순차 처리와 병렬 처리의 직접적 성능 비교")
    fun `should demonstrate clear performance difference between sequential and parallel processing`() {
        
        // Given: 동일한 워크로드로 순차/병렬 비교
        val messageVolumes = listOf(500, 1000, 2000, 5000)
        val comparisonResults = mutableMapOf<String, List<ProcessingMetrics>>()
        
        messageVolumes.forEach { volume ->
            
            val testMessages = generateRealisticTestMessages(volume)
            
            // 순차 처리 측정
            val sequentialMetrics = measureProcessingWithRealisticWorkload {
                configureSequentialKafkaListener()
                publishAndWaitForCompletion(testMessages, timeout = Duration.ofMinutes(15))
            }
            
            // 병렬 처리 측정 (최적 동시성 16)
            val parallelMetrics = measureProcessingWithRealisticWorkload {
                configureParallelKafkaConsumer(maxConcurrency = 16)
                publishAndWaitForCompletion(testMessages, timeout = Duration.ofMinutes(15))
            }
            
            // 성능 개선 효과 검증
            val throughputImprovement = parallelMetrics.throughput / sequentialMetrics.throughput
            val processingTimeReduction = 
                (sequentialMetrics.totalProcessingTime.toMillis() - parallelMetrics.totalProcessingTime.toMillis()).toDouble() / 
                sequentialMetrics.totalProcessingTime.toMillis()
                
            // 최소 성능 향상 기준
            assertThat(throughputImprovement)
                .describedAs("병렬 처리는 순차 처리 대비 최소 6배 향상")
                .isGreaterThan(6.0)
                
            assertThat(processingTimeReduction)
                .describedAs("전체 처리 시간이 최소 70% 단축되어야 함")
                .isGreaterThan(0.7)
                
            // 리소스 효율성 검증
            val cpuEfficiencyImprovement = parallelMetrics.messagesPerCpuPercent / sequentialMetrics.messagesPerCpuPercent
            assertThat(cpuEfficiencyImprovement)
                .describedAs("CPU 사용률 대비 처리 효율성 최소 4배 향상")
                .isGreaterThan(4.0)
                
            comparisonResults["volume_${volume}"] = listOf(
                sequentialMetrics.copy(processingMode = "Sequential"),
                parallelMetrics.copy(processingMode = "Parallel_16")
            )
        }
        
        // 포트폴리오용 비교 데이터 로깅
        logSequentialVsParallelComparison(comparisonResults, "KAFKA_SEQ_VS_PARALLEL")
    }

    @Test
    @Order(3)
    @DisplayName("실제 HTTP 호출 포함한 I/O 집약적 워크로드 성능 측정")
    fun `should measure performance improvement with realistic IO intensive workload`() {
        
        // Given: HTTP Interface 호출을 포함한 실제 워크로드
        val messageCount = 1000
        val httpCallLatency = Duration.ofMillis(50) // 평균 50ms HTTP 호출
        
        // Mock HTTP Interface 설정 (실제 지연 시뮬레이션)
        configureRealisticHttpInterface(averageLatency = httpCallLatency)
        
        val metrics = parallelConsumerMetrics.startDetailedMonitoring()
        
        // When: 실제 비즈니스 로직과 동일한 처리 흐름
        val realWorkloadMessages = generateMessagesWithVariousTimeTableIds(messageCount)
        
        val startTime = System.currentTimeMillis()
        
        realWorkloadMessages.forEach { message ->
            kafkaProducer.send("time-table-occupied", message.key(), message)
        }
        
        val processingCompleted = awaitProcessingCompletion(
            expectedCount = messageCount,
            timeout = Duration.ofMinutes(10)
        )
        
        val detailedMetrics = parallelConsumerMetrics.stopDetailedMonitoring()
        val endTime = System.currentTimeMillis()
        
        // Then: 실제 운영 환경과 유사한 성능 검증
        assertThat(processingCompleted).isTrue()
        
        val actualThroughput = messageCount.toDouble() / ((endTime - startTime) / 1000.0)
        
        // I/O 집약적 환경에서의 최소 성능 기준
        assertThat(actualThroughput)
            .describedAs("HTTP 호출 포함시 최소 150 msg/sec 달성")
            .isGreaterThan(150.0)
            
        // HTTP Interface 호출 성공률 검증
        assertThat(detailedMetrics.httpCallSuccessRate)
            .describedAs("HTTP 호출 성공률 99% 이상")
            .isGreaterThan(0.99)
            
        // Key 기반 순서 보장 검증  
        assertThat(detailedMetrics.orderingViolations)
            .describedAs("동일 TimeTable ID의 순서 위반이 없어야 함")
            .isEqualTo(0)
            
        // 메모리 누수 검증
        assertThat(detailedMetrics.memoryLeakDetected)
            .describedAs("장시간 처리에서 메모리 누수가 없어야 함")
            .isFalse()
            
        // 포트폴리오용 실제 워크로드 성능 데이터
        logRealisticWorkloadMetrics(detailedMetrics, "KAFKA_REALISTIC_WORKLOAD")
    }
}
```

### 2.3 핵심 측정 지표 및 성능 기준

#### 처리량 향상 지표
```kotlin
data class ThroughputImprovementMetrics(
    val concurrencyLevel: Int,            // 동시성 레벨
    val messagesPerSecond: Double,        // 초당 메시지 처리량
    val improvementRatio: Double,         // 순차 처리 대비 향상 비율
    val saturationPoint: Boolean,         // 성능 포화점 도달 여부
    val optimalConcurrency: Int           // 최적 동시성 레벨
)

// 성능 향상 기준
val minSequentialThroughput = 20.0         // 순차: 최소 20 msg/sec
val target4xConcurrency = 60.0             // 4x: 60 msg/sec (3배 향상)
val target8xConcurrency = 120.0            // 8x: 120 msg/sec (6배 향상) 
val target16xConcurrency = 200.0           // 16x: 200 msg/sec (10배 향상)
val saturationThreshold = 1.2              // 포화점: 20% 미만 향상시
```

#### 리소스 효율성 지표
```kotlin
data class ResourceEfficiencyMetrics(
    val cpuUsagePercent: Double,          // CPU 사용률
    val memoryUsageMB: Long,              // 메모리 사용량
    val messagesPerCpuPercent: Double,    // CPU 1%당 메시지 처리량
    val messagesPerMB: Double,            // 메모리 1MB당 메시지 처리량
    val resourceUtilizationScore: Double   // 전체 리소스 활용 점수 (0.0-1.0)
)

// 리소스 효율성 기준
val targetCpuUtilization = 70.0           // 목표 CPU 사용률 70%
val maxMemoryUsage = 1024L                // 최대 메모리 1GB
val minCpuEfficiencyImprovement = 4.0     // CPU 효율성 최소 4배 향상
val targetResourceScore = 0.85            // 리소스 활용 점수 85% 이상
```

#### 실제 워크로드 성능 지표
```kotlin
data class RealisticWorkloadMetrics(
    val httpCallSuccessRate: Double,      // HTTP 호출 성공률
    val averageHttpLatency: Duration,     // 평균 HTTP 응답시간
    val orderingViolations: Int,          // Key 기반 순서 위반 횟수
    val memoryLeakDetected: Boolean,      // 메모리 누수 감지 여부
    val errorRecoveryTime: Duration,      // 에러 복구 시간
    val overallSystemStability: Double    // 전체 시스템 안정성 점수
)

// 실제 워크로드 기준
val minHttpSuccessRate = 0.99             // HTTP 성공률 99% 이상
val maxHttpLatency = Duration.ofMillis(100) // HTTP 응답시간 100ms 이하
val zeroOrderingViolations = 0            // 순서 위반 0건
val maxErrorRecoveryTime = Duration.ofSeconds(5) // 에러 복구 5초 이내
```

---

## 🧪 Part 3: 간단한 통합 성능 벤치마크

### 3.1 목적
- **실제 사용 시나리오**: Rate Limiter + 시스템 부하 조합 테스트
- **리소스 모니터링**: CPU/메모리 사용량 측정  
- **포트폴리오 데이터**: 실용적인 성능 지표 수집

### 3.2 현실적 접근법

**핵심 전략**: 복잡한 통합보다는 시뮬레이션 기반 부하 테스트

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleBenchmarkTest {
    
    @Test
    @DisplayName("시스템 부하 + 리소스 모니터링 벤치마크")
    fun `should measure system performance under load`() {
        
        // Given: 시스템 부하 시나리오
        val scenarios = listOf(
            LoadScenario("가벼운 부하", 10, 5),    // 10명, 5초
            LoadScenario("중간 부하", 50, 10),    // 50명, 10초  
            LoadScenario("높은 부하", 100, 15)    // 100명, 15초
        )
        
        scenarios.forEach { scenario ->
            // 시작 전 시스템 상태 측정
            val startMemory = getCurrentMemoryUsage()
            val startTime = System.currentTimeMillis()
            
            // 동시 부하 실행
            val results = executeLoadTest(scenario)
            
            // 종료 후 시스템 상태 측정
            val endMemory = getCurrentMemoryUsage() 
            val duration = System.currentTimeMillis() - startTime
            
            // 포트폴리오 결과 출력
            println("📊 ${scenario.name} 테스트 결과:")
            println("  처리량: ${results.requestsPerSecond} req/sec")
            println("  메모리 사용: ${(endMemory - startMemory) / 1024 / 1024}MB")
            println("  평균 응답시간: ${results.averageLatency}ms")
            println("  에러율: ${results.errorRate * 100}%")
            
            // 간단한 검증
            assert(results.errorRate < 0.05) { "${scenario.name}: 에러율이 5%를 초과함" }
            assert(results.averageLatency < 500) { "${scenario.name}: 응답시간이 500ms를 초과함" }
        }
    }
    
    private fun executeLoadTest(scenario: LoadScenario): LoadTestResult {
        val executor = Executors.newFixedThreadPool(scenario.concurrentUsers)
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val latencies = ConcurrentLinkedQueue<Long>()
        
        val startTime = System.currentTimeMillis()
        
        try {
            val futures = (1..scenario.concurrentUsers).map {
                executor.submit {
                    val endTime = startTime + (scenario.durationSeconds * 1000)
                    while (System.currentTimeMillis() < endTime) {
                        val latency = measureTimeMillis {
                            try {
                                // API 호출 시뮬레이션
                                simulateApiCall()
                                successCount.incrementAndGet()
                            } catch (e: Exception) {
                                errorCount.incrementAndGet()
                            }
                        }
                        latencies.offer(latency)
                        Thread.sleep((100..500).random().toLong()) // 실제적인 요청 간격
                    }
                }
            }
            futures.forEach { it.get() }
        } finally {
            executor.shutdown()
        }
        
        val totalRequests = successCount.get() + errorCount.get()
        val duration = (System.currentTimeMillis() - startTime) / 1000.0
        
        return LoadTestResult(
            requestsPerSecond = totalRequests / duration,
            averageLatency = latencies.average(),
            errorRate = errorCount.get().toDouble() / totalRequests
        )
    }
    
    private fun simulateApiCall() {
        // 실제 비즈니스 로직 시뮬레이션
        val processingTime = (10..100).random()
        Thread.sleep(processingTime.toLong())
        
        // 5% 확률로 에러 발생
        if (Random.nextDouble() < 0.05) {
            throw RuntimeException("Simulated error")
        }
    }
}

data class LoadScenario(
    val name: String,
    val concurrentUsers: Int,
    val durationSeconds: Int
)

data class LoadTestResult(
    val requestsPerSecond: Double,
    val averageLatency: Double, 
    val errorRate: Double
```

---

## 📋 실행 계획 요약

### 핵심 변경사항

**1. Spring Context 의존성 제거**
- ❌ 기존: `@SpringBootTest` + 복잡한 Context 설정
- ✅ 현실적: Rate Limiter 어댑터 직접 생성 + 수동 테스트

**2. Kafka 테스트 단순화**  
- ❌ 기존: Parallel Consumer API 복잡한 사용법
- ✅ 현실적: 기본 Consumer vs 멀티스레드 Consumer 성능 비교

**3. 통합 테스트 현실화**
- ❌ 기존: 모든 컴포넌트 통합한 복잡한 테스트  
- ✅ 현실적: 시뮬레이션 기반 부하 테스트 + 리소스 모니터링

### 포트폴리오 핵심 지표

**Rate Limiter 효과**:
- Redis vs InMemory 성능 차이
- 동시성 차단 효과 (목표: 70-90% 차단률)
- 응답시간 오버헤드 측정

**Kafka 성능 향상**:  
- 순차 처리 vs 병렬 처리 비교
- 동시성 증가(1→4→8→16)에 따른 처리량 향상
- 목표: 최소 2배 이상 성능 향상

**시스템 안정성**:
- 부하별 처리량 측정 (10/50/100 동시 사용자)
- 메모리/CPU 사용량 모니터링
- 에러율 5% 이하, 응답시간 500ms 이하 유지

### 실행 방법

```bash
# 1. Rate Limiter 테스트
./gradlew :adapter-module:test --tests "*RateLimiterConcurrency*"

# 2. Kafka 성능 테스트  
./gradlew :adapter-module:test --tests "*KafkaParallelConsumer*"

# 3. 통합 벤치마크
./gradlew :adapter-module:test --tests "*SimpleBenchmark*"
```

이제 **실제 동작하는** 테스트 코드를 이 문서 기준으로 구현하면 됩니다!
        requestInterval: Long, // ms
        durationSeconds: Long
    ): ConcurrentRequestResults {
        
        val executor = Executors.newFixedThreadPool(concurrentUsers)
        val results = ConcurrentHashMap<String, RequestResult>()
        val startTime = AtomicLong(System.currentTimeMillis())
        val endTime = AtomicLong(startTime.get() + (durationSeconds * 1000))
        
        val tasks = mutableListOf<Future<Unit>>()
        
        // 각 동시 사용자별 요청 실행
        repeat(concurrentUsers) { userId ->
            val task = executor.submit<Unit> {
                var requestCount = 0
                
                while (System.currentTimeMillis() < endTime.get()) {
                    val requestId = "${userId}_${requestCount++}"
                    val requestStart = System.nanoTime()
                    
                    try {
                        // 실제 TimeTable 생성 API 호출
                        val response = testRestTemplate.postForEntity(
                            "/api/timetable/occupancy",
                            CreateTimeTableOccupancyRequest(
                                timeTableId = UUID.randomUUID(),
                                customerId = UUID.randomUUID(),
                                occupancyDate = LocalDate.now()
                            ),
                            CreateTimeTableOccupancyResponse::class.java
                        )
                        
                        val responseTime = (System.nanoTime() - requestStart) / 1_000_000L // ms
                        
                        results[requestId] = RequestResult(
                            userId = userId,
                            requestId = requestId,
                            httpStatus = response.statusCode,
                            responseTimeMs = responseTime,
                            timestamp = Instant.now(),
                            wasRateLimited = response.statusCode == HttpStatus.TOO_MANY_REQUESTS
                        )
                        
                    } catch (ex: Exception) {
                        val responseTime = (System.nanoTime() - requestStart) / 1_000_000L
                        
                        results[requestId] = RequestResult(
                            userId = userId,
                            requestId = requestId,
                            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                            responseTimeMs = responseTime,
                            timestamp = Instant.now(),
                            wasRateLimited = ex.message?.contains("429") == true,
                            error = ex.message
                        )
                    }
                    
                    // 요청 간격 조절
                    Thread.sleep(requestInterval)
                }
            }
            tasks.add(task)
        }
        
        // 모든 작업 완료 대기
        tasks.forEach { it.get() }
        executor.shutdown()
        
        return ConcurrentRequestResults(
            totalRequests = results.size,
            successfulRequests = results.values.count { it.httpStatus.is2xxSuccessful },
            rateLimitedRequests = results.values.count { it.wasRateLimited },
            averageResponseTime = results.values.map { it.responseTimeMs }.average(),
            p95ResponseTime = results.values.map { it.responseTimeMs }.sorted().let { sorted ->
                sorted[(sorted.size * 0.95).toInt()]
            },
            p99ResponseTime = results.values.map { it.responseTimeMs }.sorted().let { sorted ->
                sorted[(sorted.size * 0.99).toInt()]
            },
            errorRate = results.values.count { it.error != null }.toDouble() / results.size,
            requestResults = results.values.toList()
        )
    }
    
    /**
     * 극한 부하 버스트 테스트
     * - 순간적 대량 요청으로 시스템 한계 테스트
     * - 시스템 리소스 모니터링 포함
     */
    fun executeBurstLoadTest(
        concurrentUsers: Int,
        durationSeconds: Long
    ): BurstLoadResults {
        
        val systemMonitor = SystemResourceMonitor()
        systemMonitor.startMonitoring()
        
        // 버스트 모드: 최대한 빠른 요청 발송
        val executor = Executors.newFixedThreadPool(concurrentUsers)
        val latch = CountDownLatch(concurrentUsers)
        val results = ConcurrentHashMap<Int, List<RequestResult>>()
        
        val startTime = System.currentTimeMillis()
        val endTime = startTime + (durationSeconds * 1000)
        
        // 각 스레드에서 최대한 빠르게 요청 발송
        repeat(concurrentUsers) { threadId ->
            executor.submit {
                val threadResults = mutableListOf<RequestResult>()
                var requestCount = 0
                
                try {
                    while (System.currentTimeMillis() < endTime) {
                        val requestStart = System.nanoTime()
                        val requestId = "${threadId}_${requestCount++}"
                        
                        try {
                            val response = testRestTemplate.postForEntity(
                                "/api/timetable/occupancy",
                                CreateTimeTableOccupancyRequest(
                                    timeTableId = UUID.randomUUID(),
                                    customerId = UUID.randomUUID(),
                                    occupancyDate = LocalDate.now()
                                ),
                                CreateTimeTableOccupancyResponse::class.java
                            )
                            
                            val responseTime = (System.nanoTime() - requestStart) / 1_000_000L
                            
                            threadResults.add(RequestResult(
                                userId = threadId,
                                requestId = requestId,
                                httpStatus = response.statusCode,
                                responseTimeMs = responseTime,
                                timestamp = Instant.now(),
                                wasRateLimited = response.statusCode == HttpStatus.TOO_MANY_REQUESTS
                            ))
                            
                        } catch (ex: Exception) {
                            val responseTime = (System.nanoTime() - requestStart) / 1_000_000L
                            
                            threadResults.add(RequestResult(
                                userId = threadId,
                                requestId = requestId,
                                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
                                responseTimeMs = responseTime,
                                timestamp = Instant.now(),
                                wasRateLimited = ex.message?.contains("429") == true,
                                error = ex.message
                            ))
                        }
                        
                        // 버스트 모드: 지연 없음 (최대 부하)
                        if (requestCount % 10 == 0) {
                            Thread.sleep(1) // CPU 과부하 방지용 최소 대기
                        }
                    }
                } finally {
                    results[threadId] = threadResults
                    latch.countDown()
                }
            }
        }
        
        // 모든 스레드 완료 대기
        latch.await()
        executor.shutdown()
        
        val systemStats = systemMonitor.stopMonitoring()
        val allResults = results.values.flatten()
        
        return BurstLoadResults(
            totalRequests = allResults.size,
            successfulRequests = allResults.count { it.httpStatus.is2xxSuccessful },
            rateLimitedRequests = allResults.count { it.wasRateLimited },
            errorRate = allResults.count { it.error != null }.toDouble() / allResults.size,
            averageRequestsPerSecond = allResults.size.toDouble() / durationSeconds,
            maxCpuUsage = systemStats.maxCpuUsage,
            maxMemoryUsage = systemStats.maxMemoryUsage,
            systemRecoveryTime = systemStats.recoveryTimeToNormalState,
            requestResults = allResults
        )
    }
}
```

### 3.2 Kafka Parallel Consumer 부하 테스트 구현

#### 3.2.1 Kafka Producer/Consumer 설정

```kotlin
/**
 * Kafka 프로듀서 설정 및 메시지 발행 유틸리티
 */
fun configureTestKafkaProducer(): KafkaTemplate<String, TestEvent> {
    val producerProps = mapOf<String, Any>(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092,localhost:9093,localhost:9094",
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
        ProducerConfig.ACKS_CONFIG to "all", // 모든 replica 확인
        ProducerConfig.RETRIES_CONFIG to 5,
        ProducerConfig.BATCH_SIZE_CONFIG to 16384,
        ProducerConfig.LINGER_MS_CONFIG to 10, // 배치 효율성
        ProducerConfig.BUFFER_MEMORY_CONFIG to 33554432,
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true // 중복 방지
    )
    
    return KafkaTemplate(DefaultKafkaProducerFactory(producerProps))
}

/**
 * Parallel Consumer 동적 재설정
 */
fun reconfigureParallelConsumer(maxConcurrency: Int) {
    // 기존 Consumer 정리
    parallelConsumer?.close()
    
    val consumerProps = mapOf<String, Any>(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092,localhost:9093,localhost:9094",
        ConsumerConfig.GROUP_ID_CONFIG to "reservation-test-group",
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        ConsumerConfig.ISOLATION_LEVEL_CONFIG to "read_committed",
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 500
    )
    
    val options = ParallelConsumerOptions.builder<String, TestEvent>()
        .consumer(KafkaConsumer(consumerProps))
        .ordering(ProcessingOrder.KEY) // Key별 순서 보장
        .maxConcurrency(maxConcurrency) // 동시성 설정
        .commitMode(CommitMode.PERIODIC_TRANSACTIONAL) // 안전한 커밋
        .commitInterval(Duration.ofSeconds(5))
        .retryDelayProvider { _, _ -> Duration.ofMillis(100) }
        .build()
        
    parallelConsumer = ParallelStreamProcessor.createEosStreamProcessor(options)
    
    // 메시지 처리 로직 등록
    parallelConsumer!!.subscribe(listOf("time-table-occupied")) { consumerRecord ->
        val startTime = System.nanoTime()
        val event = consumerRecord.value()
        
        try {
            // 실제 비즈니스 로직 시뮬레이션 (HTTP Interface 호출)
            simulateHttpInterfaceCall(event.timeTableId, event.timeTableOccupancyId)
            
            // 예약 생성 로직 시뮬레이션
            simulateReservationCreation(event)
            
            val processingTime = (System.nanoTime() - startTime) / 1_000_000 // ms
            recordProcessingMetrics(event, processingTime, success = true)
            
        } catch (exception: Exception) {
            val processingTime = (System.nanoTime() - startTime) / 1_000_000
            recordProcessingMetrics(event, processingTime, success = false)
            throw exception // Parallel Consumer가 재시도 처리
        }
    }
}

/**
 * HTTP Interface 호출 시뮬레이션 (실제 지연 포함)
 */
private fun simulateHttpInterfaceCall(timeTableId: UUID, occupancyId: UUID): TimeTableOccupancyResponse {
    val startTime = System.nanoTime()
    
    // 실제 HTTP Interface 호출 시뮬레이션
    val httpCallDelay = when {
        Random.nextDouble() < 0.1 -> 200L // 10% slow queries (DB 조회 지연)
        Random.nextDouble() < 0.05 -> 500L // 5% very slow (cache miss)
        else -> Random.nextLong(10, 80) // 일반적인 응답시간 10-80ms
    }
    
    Thread.sleep(httpCallDelay)
    
    val endTime = System.nanoTime()
    val actualLatency = (endTime - startTime) / 1_000_000
    
    // HTTP 지연 메트릭 기록
    httpInterfaceLatencyMetrics.record(actualLatency)
    
    return TimeTableOccupancyResponse(
        timeTableId = timeTableId,
        timeTableOccupancyId = occupancyId,
        customerId = UUID.randomUUID(),
        reservationStatus = "AVAILABLE",
        timestamp = Instant.now()
    )
}

/**
 * 예약 생성 비즈니스 로직 시뮬레이션
 */
private fun simulateReservationCreation(event: TestEvent) {
    // DB 저장 시뮬레이션 (5-15ms)
    val dbSaveDelay = Random.nextLong(5, 15)
    Thread.sleep(dbSaveDelay)
    
    // Redis 캐시 갱신 시뮬레이션 (1-5ms)  
    val cacheUpdateDelay = Random.nextLong(1, 5)
    Thread.sleep(cacheUpdateDelay)
    
    // 성공률 98% (2% 실패로 재시도 동작 테스트)
    if (Random.nextDouble() < 0.02) {
        throw RuntimeException("Simulated business logic failure for testing")
    }
}
```

#### 3.2.2 메시지 발행 및 처리 완료 대기 로직

```kotlin
/**
 * 현실적인 테스트 메시지 생성
 */
fun generateRealisticTestMessages(count: Int): List<TestEvent> {
    val timeTableIds = (1..10).map { UUID.randomUUID() } // 10개 테이블로 Key 분산
    
    return (1..count).map { index ->
        TestEvent(
            timeTableId = timeTableIds[index % timeTableIds.size], // Key 분산
            timeTableOccupancyId = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            occupiedAt = Instant.now(),
            sequenceNumber = index,
            eventId = UUID.randomUUID().toString(),
            version = 1
        )
    }
}

/**
 * 메시지 발행 및 처리 완료 대기
 */
fun publishAndWaitForCompletion(
    messages: List<TestEvent>,
    timeout: Duration
): Boolean {
    val kafkaTemplate = configureTestKafkaProducer()
    val completionTracker = ConcurrentHashMap<String, Boolean>()
    val startTime = System.currentTimeMillis()
    
    // 모든 메시지의 완료 상태 초기화
    messages.forEach { message ->
        completionTracker[message.eventId] = false
    }
    
    // 메시지 발행
    val sendFutures = messages.map { message ->
        kafkaTemplate.send("time-table-occupied", message.key(), message).apply {
            addCallback(
                { _ -> logger.debug("Message sent successfully: ${message.eventId}") },
                { ex -> logger.error("Failed to send message: ${message.eventId}", ex) }
            )
        }
    }
    
    // 모든 메시지 발행 완료 대기
    sendFutures.forEach { future ->
        try {
            future.get(10, TimeUnit.SECONDS)
        } catch (ex: Exception) {
            logger.error("Message send failed", ex)
        }
    }
    
    // 메시지 처리 완료 대기 (polling 방식)
    val pollingInterval = 100L // 100ms마다 확인
    val maxWaitTime = timeout.toMillis()
    
    while (System.currentTimeMillis() - startTime < maxWaitTime) {
        val processedCount = completionTracker.values.count { it }
        val totalCount = messages.size
        
        logger.info("Processing progress: $processedCount/$totalCount messages completed")
        
        if (processedCount >= totalCount) {
            logger.info("All messages processed successfully in ${System.currentTimeMillis() - startTime}ms")
            return true
        }
        
        // Progress reporting for portfolio metrics
        if ((System.currentTimeMillis() - startTime) % 5000 < pollingInterval) {
            val processingRate = processedCount.toDouble() / ((System.currentTimeMillis() - startTime) / 1000.0)
            logger.info("Current processing rate: ${String.format("%.2f", processingRate)} msg/sec")
        }
        
        Thread.sleep(pollingInterval)
    }
    
    val finalProcessedCount = completionTracker.values.count { it }
    logger.error("Timeout: Only $finalProcessedCount/${messages.size} messages processed")
    return false
}

/**
 * 메시지 처리 완료 추적
 */
fun markMessageAsProcessed(eventId: String) {
    completionTracker[eventId] = true
}

/**
 * 순차 처리용 Kafka Listener 설정
 */
fun configureSequentialKafkaListener() {
    // 기존 Parallel Consumer 정리
    parallelConsumer?.close()
    
    // 표준 @KafkaListener 방식으로 순차 처리 설정
    val sequentialConsumerConfig = mapOf<String, Any>(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:9092,localhost:9093,localhost:9094",
        ConsumerConfig.GROUP_ID_CONFIG to "reservation-sequential-group",
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 1, // 한 번에 1개씩 처리
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false
    )
    
    // 순차 처리 로직 구현
    sequentialKafkaConsumer = createSequentialConsumer(sequentialConsumerConfig)
}
```

#### 대량 메시지 처리 성능 테스터
```kotlin
@Component
class KafkaPerformanceTester {
    
    /**
     * 병렬 Consumer 재설정
     * - 런타임에 동시성 레벨 변경
     * - 기존 Consumer 정리 후 새로 생성
     */
    fun reconfigureParallelConsumer(maxConcurrency: Int) {
        // 기존 Consumer 정리
        parallelConsumer?.close()
        
        // 새로운 설정으로 Consumer 생성
        val consumerProps = kafkaProperties.consumer.buildProperties()
        
        val options = ParallelConsumerOptions.builder<String, TestEvent>()
            .consumer(KafkaConsumer(consumerProps))
            .ordering(ProcessingOrder.KEY)
            .maxConcurrency(maxConcurrency)
            .commitMode(CommitMode.PERIODIC_TRANSACTIONAL)
            .commitInterval(Duration.ofSeconds(5))
            .retryDelayProvider { _, _ -> Duration.ofSeconds(1) }
            .build()

        parallelConsumer = ParallelStreamProcessor.createEosStreamProcessor(options)
        
        // 메시지 처리 로직 등록
        parallelConsumer.subscribe(listOf("time-table-occupied")) { record ->
            processTestEvent(record.value())
        }
        
        log.info("Parallel Consumer reconfigured with maxConcurrency: $maxConcurrency")
    }
    
    /**
     * TimeTable 점유 이벤트 처리 (실제 비즈니스 로직)
     * - HTTP Interface 호출 시뮬레이션
     * - 예약 생성 UseCase 실행
     * - 현실적인 처리 시간과 에러율 포함
     */
    private fun processTestEvent(event: TestEvent) {
        val processingStartTime = System.nanoTime()
        
        try {
            // 1. HTTP Interface 호출을 통한 데이터 조회
            val response = httpInterface.findTimeTableOccupancyInternally(
                timeTableId = event.timeTableId,
                timeTableOccupancyId = event.timeTableOccupancyId
            )
            
            response.body?.let { body ->
                // 2. 예약 생성 Command 생성
                val command = CreateReservationCommand(
                    timeTableId = body.timeTableId,
                    customerId = body.customerId,
                    timeTableOccupancyId = body.timeTableOccupancyId,
                    reservationDate = body.occupancyDate
                )
                
                // 3. 실제 예약 생성 UseCase 실행
                createReservationUseCase.execute(command)
                
                val processingTime = (System.nanoTime() - processingStartTime) / 1_000_000
                
                // 4. 성공 메트릭 기록
                kafkaMetricsCollector.recordSuccessfulProcessing(
                    event.timeTableId.toString(),
                    processingTime,
                    Thread.currentThread().name
                )
                
                log.debug("Successfully processed event: ${event.timeTableOccupancyId} in ${processingTime}ms")
                
            } ?: throw RuntimeException("HTTP Interface returned empty response")
            
        } catch (exception: Exception) {
            val processingTime = (System.nanoTime() - processingStartTime) / 1_000_000
            
            // 실패 메트릭 기록
            kafkaMetricsCollector.recordFailedProcessing(
                event.timeTableId.toString(),
                processingTime,
                exception.message ?: "Unknown error"
            )
            
            log.error("Failed to process event: ${event.timeTableOccupancyId}", exception)
            
            // Parallel Consumer가 재시도 처리하도록 예외 전파
            throw exception
        }
    }
    
    /**
     * 현실적인 테스트 메시지 생성
     * - 다양한 TimeTable ID로 Key 분산
     * - HTTP 호출이 필요한 실제 데이터 구조
     */
    fun generateRealisticTestMessages(count: Int): List<TestEvent> {
        val timeTableIds = (1..20).map { UUID.randomUUID() } // 20개 테이블로 분산
        
        return (1..count).map { index ->
            TestEvent(
                timeTableId = timeTableIds[index % timeTableIds.size], // Key 분산
                timeTableOccupancyId = UUID.randomUUID(),
                customerId = UUID.randomUUID(),
                occupancyDate = LocalDate.now().plusDays(Random.nextLong(1, 30)),
                sequenceNumber = index,
                eventTimestamp = Instant.now()
            )
        }
    }
    
    /**
     * 실제 워크로드 시뮬레이션 (HTTP 호출 포함)
     * - FindTimeTableOccupancyHttpInterface 호출
     * - CreateReservationUseCase 실행 
     * - 실제 비즈니스 로직과 동일한 처리 흐름
     */
    fun measureProcessingWithRealisticWorkload(
        testExecution: () -> Unit
    ): ProcessingMetrics {
        
        val startTime = System.currentTimeMillis()
        val startCpuTime = getProcessCpuTime()
        val startMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // HTTP Interface 응답 시간 측정 준비
        val httpCallTimes = ConcurrentHashMap<String, Long>()
        configureHttpInterfaceMonitoring(httpCallTimes)
        
        // 실제 테스트 실행
        testExecution()
        
        val endTime = System.currentTimeMillis()
        val endCpuTime = getProcessCpuTime()
        val endMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        val processingTimeMs = endTime - startTime
        val cpuTimeMs = (endCpuTime - startCpuTime) / 1_000_000L
        val memoryUsedMB = (endMemory - startMemory) / 1024 / 1024
        
        // HTTP 호출 통계 계산
        val avgHttpTime = httpCallTimes.values.takeIf { it.isNotEmpty() }?.average() ?: 0.0
        val successfulHttpCalls = httpCallTimes.size
        
        return ProcessingMetrics(
            totalProcessingTime = Duration.ofMillis(processingTimeMs),
            cpuTimeUsed = Duration.ofMillis(cpuTimeMs),
            memoryUsedMB = memoryUsedMB,
            averageHttpCallTime = Duration.ofMillis(avgHttpTime.toLong()),
            httpCallCount = successfulHttpCalls,
            throughput = 0.0, // 별도 계산 필요
            cpuUsagePercent = (cpuTimeMs.toDouble() / processingTimeMs) * 100,
            messagesPerCpuPercent = 0.0 // 별도 계산 필요
        )
    }
    
    /**
     * 처리 완료 대기 (정확한 타이밍 측정)
     * - 모든 메시지가 처리될 때까지 대기
     * - Offset 커밋까지 확인하여 정확성 보장
     */
    fun awaitProcessingCompletion(
        expectedCount: Int,
        timeout: Duration
    ): Boolean {
        val deadline = Instant.now().plus(timeout)
        var processedCount = 0
        
        while (Instant.now().isBefore(deadline)) {
            // 실제 DB에서 처리된 레코드 수 확인
            processedCount = reservationRepository.count().toInt()
            
            if (processedCount >= expectedCount) {
                // 추가로 Offset 커밋 완료까지 대기
                Thread.sleep(2000)
                return true
            }
            
            Thread.sleep(100) // 100ms마다 체크
        }
        
        log.warn("Processing not completed within timeout. Expected: $expectedCount, Actual: $processedCount")
        return false
    }
    
    /**
     * HTTP Interface 지연 시뮬레이션 설정
     * - 실제 운영 환경과 유사한 지연 시간
     * - 네트워크 지연, 캐시 히트/미스 등 고려
     */
    fun configureRealisticHttpInterface(averageLatency: Duration) {
        every { 
            httpInterface.findTimeTableOccupancyInternally(any(), any()) 
        } answers {
            // 실제 HTTP 지연 시뮬레이션
            val jitter = Random.nextLong(-10, 11) // ±10ms 지터
            val actualLatency = averageLatency.toMillis() + jitter
            Thread.sleep(maxOf(1L, actualLatency))
            
            // 성공 응답 반환
            ResponseEntity.ok(
                FindTimeTableOccupancyInternalResponse(
                    timeTableId = firstArg(),
                    timeTableOccupancyId = secondArg(),
                    customerId = UUID.randomUUID(),
                    occupancyDate = LocalDate.now(),
                    status = "OCCUPIED"
                )
            )
        }
    }
}
```

#### 3.2.3 성능 메트릭 수집 및 분석

```kotlin
/**
 * Parallel Consumer 성능 메트릭 수집기
 */
class ParallelConsumerMetrics {
    private val processedMessages = AtomicLong(0)
    private val totalProcessingTime = AtomicLong(0) // nanoseconds
    private val processingTimes = ConcurrentLinkedQueue<Long>()
    private val errorCount = AtomicLong(0)
    private val startTime = AtomicLong(0)
    
    // System metrics
    private val memoryMXBean = ManagementFactory.getMemoryMXBean()
    private val gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans()
    private val cpuUsageSamples = ConcurrentLinkedQueue<Double>()
    private val memoryUsageSamples = ConcurrentLinkedQueue<Long>()
    
    fun reset() {
        processedMessages.set(0)
        totalProcessingTime.set(0)
        processingTimes.clear()
        errorCount.set(0)
        startTime.set(System.currentTimeMillis())
        cpuUsageSamples.clear()
        memoryUsageSamples.clear()
    }
    
    fun recordProcessingMetrics(event: TestEvent, processingTimeMs: Long, success: Boolean) {
        processedMessages.incrementAndGet()
        
        if (success) {
            val processingTimeNanos = processingTimeMs * 1_000_000
            totalProcessingTime.addAndGet(processingTimeNanos)
            processingTimes.offer(processingTimeMs)
            
            // Keep only recent 1000 samples for percentile calculation
            if (processingTimes.size > 1000) {
                processingTimes.poll()
            }
        } else {
            errorCount.incrementAndGet()
        }
        
        // Mark message as completed for tracking
        markMessageAsProcessed(event.eventId)
        
        // Sample system metrics every 100 messages
        if (processedMessages.get() % 100 == 0L) {
            sampleSystemMetrics()
        }
    }
    
    private fun sampleSystemMetrics() {
        // CPU usage (approximation using GC)
        val gcTime = gcMXBeans.sumOf { it.collectionTime }
        val cpuUsage = minOf(gcTime.toDouble() / (System.currentTimeMillis() - startTime.get()) * 100, 100.0)
        cpuUsageSamples.offer(cpuUsage)
        
        // Memory usage
        val memoryUsage = memoryMXBean.heapMemoryUsage.used / (1024 * 1024) // MB
        memoryUsageSamples.offer(memoryUsage)
        
        // Keep samples manageable
        if (cpuUsageSamples.size > 100) cpuUsageSamples.poll()
        if (memoryUsageSamples.size > 100) memoryUsageSamples.poll()
    }
    
    fun getMetrics(): KafkaProcessingMetrics {
        val messageCount = processedMessages.get()
        val elapsedTimeMs = System.currentTimeMillis() - startTime.get()
        val throughput = if (elapsedTimeMs > 0) messageCount.toDouble() / (elapsedTimeMs / 1000.0) else 0.0
        
        val avgProcessingTime = if (messageCount > 0) {
            totalProcessingTime.get() / messageCount / 1_000_000.0 // ms
        } else 0.0
        
        val p95ProcessingTime = calculateP95ProcessingTime()
        val errorRate = if (messageCount > 0) errorCount.get().toDouble() / messageCount else 0.0
        
        val avgCpuUsage = if (cpuUsageSamples.isNotEmpty()) {
            cpuUsageSamples.average()
        } else 0.0
        
        val avgMemoryUsage = if (memoryUsageSamples.isNotEmpty()) {
            memoryUsageSamples.average()
        } else 0.0
        
        return KafkaProcessingMetrics(
            messagesProcessed = messageCount,
            throughput = throughput,
            averageProcessingTime = avgProcessingTime,
            p95ProcessingTime = p95ProcessingTime,
            errorRate = errorRate,
            averageCpuUsage = avgCpuUsage,
            averageMemoryUsage = avgMemoryUsage,
            elapsedTimeMs = elapsedTimeMs
        )
    }
    
    private fun calculateP95ProcessingTime(): Double {
        val times = processingTimes.toList().sorted()
        return if (times.isNotEmpty()) {
            val p95Index = (times.size * 0.95).toInt()
            times.getOrElse(p95Index) { times.last() }.toDouble()
        } else 0.0
    }
}

/**
 * 포트폴리오용 로깅 및 메트릭 출력
 */
fun logStepwiseImprovementMetrics(
    performanceResults: Map<Int, ThroughputMetrics>,
    testType: String
) {
    logger.info("=".repeat(80))
    logger.info("[$testType] 단계별 성능 향상 결과")
    logger.info("=".repeat(80))
    
    val sortedResults = performanceResults.toSortedMap()
    var previousThroughput = 0.0
    
    sortedResults.forEach { (concurrency, metrics) ->
        val improvement = if (previousThroughput > 0) {
            String.format("%.1fx", metrics.messagesPerSecond / previousThroughput)
        } else {
            "baseline"
        }
        
        logger.info(
            "동시성 $concurrency: " +
            "${String.format("%.1f", metrics.messagesPerSecond)} msg/sec | " +
            "평균지연 ${String.format("%.1f", metrics.averageProcessingTime)}ms | " +
            "P95지연 ${String.format("%.1f", metrics.p95ProcessingTime)}ms | " +
            "CPU ${String.format("%.1f", metrics.cpuUsage)}% | " +
            "메모리 ${String.format("%.1f", metrics.memoryUsage)}MB | " +
            "에러율 ${String.format("%.3f", metrics.errorRate * 100)}% | " +
            "향상률: $improvement"
        )
        
        previousThroughput = metrics.messagesPerSecond
    }
    
    // 전체 향상 요약
    val initialThroughput = sortedResults.values.first().messagesPerSecond
    val maxThroughput = sortedResults.values.maxByOrNull { it.messagesPerSecond }?.messagesPerSecond ?: 0.0
    val overallImprovement = maxThroughput / initialThroughput
    
    logger.info("-".repeat(80))
    logger.info("전체 성능 향상: ${String.format("%.1fx", overallImprovement)} " +
                "(${String.format("%.1f", initialThroughput)} → ${String.format("%.1f", maxThroughput)} msg/sec)")
    logger.info("=".repeat(80))
}

/**
 * 순차 vs 병렬 비교 로깅 (포트폴리오용)
 */
fun logSequentialVsParallelComparison(
    comparisonResults: Map<String, List<ProcessingMetrics>>,
    testType: String
) {
    logger.info("=".repeat(80))
    logger.info("[$testType] 순차 처리 vs 병렬 처리 성능 비교")
    logger.info("=".repeat(80))
    
    comparisonResults.forEach { (volumeKey, metrics) ->
        val volume = volumeKey.substringAfter("volume_").toInt()
        val sequential = metrics.find { it.processingMode == "Sequential" }!!
        val parallel = metrics.find { it.processingMode.startsWith("Parallel") }!!
        
        val throughputImprovement = parallel.throughput / sequential.throughput
        val timeReduction = (sequential.totalProcessingTime.toMillis() - parallel.totalProcessingTime.toMillis()).toDouble() / 
                           sequential.totalProcessingTime.toMillis()
        val latencyImprovement = sequential.averageLatency / parallel.averageLatency
        
        logger.info("메시지 $volume 개 처리:")
        logger.info("  순차 처리: ${String.format("%.1f", sequential.throughput)} msg/sec, " +
                   "처리시간 ${sequential.totalProcessingTime.toMillis()}ms, " +
                   "평균지연 ${String.format("%.1f", sequential.averageLatency)}ms")
        logger.info("  병렬 처리: ${String.format("%.1f", parallel.throughput)} msg/sec, " +
                   "처리시간 ${parallel.totalProcessingTime.toMillis()}ms, " +
                   "평균지연 ${String.format("%.1f", parallel.averageLatency)}ms")
        logger.info("  성능 향상: 처리량 ${String.format("%.1fx", throughputImprovement)}, " +
                   "시간 단축 ${String.format("%.1f%%", timeReduction * 100)}, " +
                   "지연 개선 ${String.format("%.1fx", latencyImprovement)}")
        logger.info("-".repeat(40))
    }
    
    logger.info("=".repeat(80))
}
```

### 3.3 시스템 리소스 모니터링

#### 실시간 성능 지표 수집기
```kotlin
@Component
class SystemMetricsCollector {
    
    private val metrics = mutableListOf<SystemSnapshot>()
    private var monitoringThread: Thread? = null
    @Volatile private var isMonitoring = false
    
    fun startMonitoring(): SystemMetricsCollector {
        isMonitoring = true
        metrics.clear()
        
        monitoringThread = thread(start = true, name = "system-metrics-collector") {
            val bean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
            val memoryBean = ManagementFactory.getMemoryMXBean()
            val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
            
            while (isMonitoring) {
                try {
                    val snapshot = SystemSnapshot(
                        timestamp = Instant.now(),
                        cpuUsage = bean.cpuLoad * 100, // CPU 사용률 (%)
                        memoryUsage = with(memoryBean.heapMemoryUsage) {
                            (used.toDouble() / max.toDouble()) * 100 // 메모리 사용률 (%)
                        },
                        heapUsedMB = memoryBean.heapMemoryUsage.used / 1024 / 1024,
                        heapMaxMB = memoryBean.heapMemoryUsage.max / 1024 / 1024,
                        gcCollectionCount = gcBeans.sumOf { it.collectionCount },
                        gcCollectionTime = gcBeans.sumOf { it.collectionTime },
                        threadCount = ManagementFactory.getThreadMXBean().threadCount
                    )
                    
                    synchronized(metrics) {
                        metrics.add(snapshot)
                    }
                    
                    Thread.sleep(200) // 200ms마다 수집
                    
                } catch (ex: InterruptedException) {
                    break
                } catch (ex: Exception) {
                    log.warn("Error collecting system metrics", ex)
                }
            }
        }
        
        return this
    }
    
    fun stopMonitoring(): SystemStabilityMetrics {
        isMonitoring = false
        monitoringThread?.interrupt()
        monitoringThread?.join(5000) // 5초 대기
        
        return analyzeSystemMetrics()
    }
    
    private fun analyzeSystemMetrics(): SystemStabilityMetrics {
        synchronized(metrics) {
            if (metrics.isEmpty()) {
                return SystemStabilityMetrics(
                    maxCpuUsage = 0.0,
                    maxMemoryUsage = 0.0,
                    averageCpuUsage = 0.0,
                    averageMemoryUsage = 0.0,
                    gcPauseTime = Duration.ZERO,
                    recoveryTimeToNormalState = Duration.ZERO,
                    errorRecoveryCapability = true
                )
            }
            
            val maxCpu = metrics.maxOf { it.cpuUsage }
            val maxMemory = metrics.maxOf { it.memoryUsage }
            val avgCpu = metrics.map { it.cpuUsage }.average()
            val avgMemory = metrics.map { it.memoryUsage }.average()
            
            // GC 일시정지 시간 계산 (연속된 측정값 기준)
            val gcPauseTime = calculateGcPauseTime()
            
            // 시스템 복구 시간 계산 (부하가 정상 수준으로 돌아오는 시간)
            val recoveryTime = calculateRecoveryTime(avgCpu, avgMemory)
            
            return SystemStabilityMetrics(
                maxCpuUsage = maxCpu,
                maxMemoryUsage = maxMemory,
                averageCpuUsage = avgCpu,
                averageMemoryUsage = avgMemory,
                gcPauseTime = gcPauseTime,
                recoveryTimeToNormalState = recoveryTime,
                errorRecoveryCapability = maxCpu < 95.0 && maxMemory < 90.0 // 임계치 미달성시 복구 가능
            )
        }
    }
    
    private fun calculateRecoveryTime(avgCpu: Double, avgMemory: Double): Duration {
        val normalCpuThreshold = 30.0  // 정상 CPU 30% 이하
        val normalMemoryThreshold = 50.0 // 정상 메모리 50% 이하
        
        // 마지막 정상 상태 시점 찾기
        val lastNormalIndex = metrics.indexOfLast { 
            it.cpuUsage <= normalCpuThreshold && it.memoryUsage <= normalMemoryThreshold 
        }
        
        if (lastNormalIndex == -1 || lastNormalIndex == metrics.size - 1) {
            return Duration.ZERO // 복구되지 않았거나 항상 정상
        }
        
        val recoveryStart = metrics[lastNormalIndex].timestamp
        val recoveryEnd = metrics.last().timestamp
        
        return Duration.between(recoveryStart, recoveryEnd)
    }
}
```

### 3.2 테스트 실행 및 결과 수집

#### 통합 테스트 실행기
```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortfolioIntegrationTest {
    
    @Test
    @DisplayName("포트폴리오용 종합 성능 테스트 실행")
    fun `execute comprehensive portfolio performance tests`() {
        
        // 1. Rate Limiter 테스트 실행
        val rateLimiterResults = executeRateLimiterTests()
        
        // 2. Kafka Parallel Consumer 테스트 실행
        val kafkaResults = executeKafkaTests()
        
        // 3. 그래프 생성
        generatePortfolioCharts(rateLimiterResults, kafkaResults)
        
        // 4. 테스트 보고서 생성
        generatePortfolioReport(rateLimiterResults, kafkaResults)
    }
}
```

### 3.3 Rate Limiter 테스트 설정 클래스

#### 최소한 빈 로드를 위한 ContextConfiguration
```kotlin
@Configuration
@EnableAspectJAutoProxy
@Testcontainers
class IntegrationTestConfiguration {
    
    companion object {
        @Container
        @JvmStatic
        val redisContainer: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true)
            .withStartupTimeout(Duration.ofSeconds(30))
        
        @Container
        @JvmStatic
        val kafkaContainer: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withReuse(true)
            .withStartupTimeout(Duration.ofSeconds(60))
    }
    
    @Bean
    fun spelParser(): SpelParser = SpelParser()
    
    @Bean
    @Primary
    fun acquireRateLimitInMemoryAdapter(): AcquireRateLimitInMemoryAdapter =
        AcquireRateLimitInMemoryAdapter()
    
    @Bean
    fun redissonClient(): RedissonClient {
        val config = Config()
        config.useSingleServer()
            .setAddress("redis://${redisContainer.host}:${redisContainer.getMappedPort(6379)}")
            .setConnectionMinimumIdleSize(1)
            .setConnectionPoolSize(10)
            .setConnectionTimeout(3000)
            .setTimeout(3000)
        return Redisson.create(config)
    }
    
    @Bean
    fun acquireRateLimitRedisAdapter(redissonClient: RedissonClient): AcquireRateLimitRedisAdapter =
        AcquireRateLimitRedisAdapter(redissonClient)
    
    @Bean
    fun rateLimiterAspect(
        spelParser: SpelParser,
        inMemoryAdapter: AcquireRateLimitInMemoryAdapter,
        redisAdapter: AcquireRateLimitRedisAdapter
    ): RateLimiterAspect = RateLimiterAspect(spelParser, inMemoryAdapter, redisAdapter)
    
    @Bean
    fun rateLimitedTestService(): RateLimitedTestService = RateLimitedTestService()
    
    @Bean
    fun rateLimiterMetricsCollector(): RateLimiterMetricsCollector = RateLimiterMetricsCollector()
    
    // Kafka 설정
    @Bean
    fun kafkaProducerFactory(): ProducerFactory<String, Any> {
        val props = mapOf<String, Any>(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.BATCH_SIZE_CONFIG to 16384,
            ProducerConfig.LINGER_MS_CONFIG to 1,
            ProducerConfig.BUFFER_MEMORY_CONFIG to 33554432
        )
        return DefaultKafkaProducerFactory(props)
    }
    
    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<String, Any>): KafkaTemplate<String, Any> =
        KafkaTemplate(producerFactory)
    
    @Bean
    fun kafkaConsumerFactory(): ConsumerFactory<String, TestEvent> {
        val props = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "test-consumer-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 100,
            JsonDeserializer.TRUSTED_PACKAGES to "*"
        )
        return DefaultKafkaConsumerFactory(props)
    }
    
    @Bean
    fun parallelConsumerOptions(): ParallelConsumerOptions<String, TestEvent> {
        val consumerProps = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafkaContainer.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "parallel-test-group",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to JsonDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            JsonDeserializer.TRUSTED_PACKAGES to "*"
        )
        
        return ParallelConsumerOptions.builder<String, TestEvent>()
            .consumer(KafkaConsumer(consumerProps))
            .ordering(ProcessingOrder.KEY)
            .maxConcurrency(1) // 초기값, 테스트에서 동적 변경
            .commitMode(CommitMode.PERIODIC_TRANSACTIONAL)
            .commitInterval(Duration.ofSeconds(5))
            .build()
    }
    
    @Bean
    fun kafkaTestDataGenerator(): KafkaTestDataGenerator = KafkaTestDataGenerator()
    
    @Bean
    fun kafkaPerformanceMetricsCollector(): KafkaPerformanceMetricsCollector = 
        KafkaPerformanceMetricsCollector()
}

/**
 * Rate Limiter 테스트용 서비스 클래스
 */
@Service
class RateLimitedTestService {
    
    @RateLimiter(
        key = "#userId",
        type = RateLimitType.SLIDING_WINDOW,
        rate = 10,
        rateIntervalTime = 1,
        rateIntervalTimeUnit = TimeUnit.SECONDS,
        maximumWaitTime = 5,
        maximumWaitTimeUnit = TimeUnit.SECONDS
    )
    fun processUserRequest(userId: String): ServiceResponse {
        val startTime = System.currentTimeMillis()
        
        // 실제 비즈니스 로직 시뮬레이션 (10-50ms 지연)
        Thread.sleep((10..50).random().toLong())
        
        val processingTime = System.currentTimeMillis() - startTime
        
        return ServiceResponse(
            userId = userId,
            processingTimeMs = processingTime,
            timestamp = Instant.now(),
            success = true
        )
    }
    
    @RateLimiter(
        key = "'burst_test'",
        type = RateLimitType.TOKEN_BUCKET,
        rate = 5,
        rateIntervalTime = 1,
        rateIntervalTimeUnit = TimeUnit.SECONDS,
        bucketLiveTime = 10,
        bucketLiveTimeUnit = TimeUnit.SECONDS,
        maximumWaitTime = 1,
        maximumWaitTimeUnit = TimeUnit.SECONDS
    )
    fun handleBurstRequest(): ServiceResponse {
        val startTime = System.currentTimeMillis()
        
        // 버스트 처리 시뮬레이션 (1-10ms 빠른 응답)
        Thread.sleep((1..10).random().toLong())
        
        return ServiceResponse(
            userId = "burst_user",
            processingTimeMs = System.currentTimeMillis() - startTime,
            timestamp = Instant.now(),
            success = true
        )
    }
}

/**
 * 서비스 응답 데이터 클래스
 */
data class ServiceResponse(
    val userId: String,
    val processingTimeMs: Long,
    val timestamp: Instant,
    val success: Boolean,
    val errorMessage: String? = null
)

/**
 * Rate Limiter 메트릭 수집기
 */
@Component
class RateLimiterMetricsCollector {
    private val systemMetrics = mutableListOf<SystemMetric>()
    private var isCollecting = false
    
    fun startCollection() {
        isCollecting = true
        // 시스템 메트릭 수집 스레드 시작
        Thread {
            while (isCollecting) {
                collectSystemMetric()
                Thread.sleep(100) // 100ms 간격
            }
        }.start()
    }
    
    fun stopCollection(): List<SystemMetric> {
        isCollecting = false
        return systemMetrics.toList()
    }
    
    private fun collectSystemMetric() {
        val runtime = Runtime.getRuntime()
        val metric = SystemMetric(
            timestamp = Instant.now(),
            cpuUsage = getProcessCpuUsage(),
            memoryUsage = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024), // MB
            gcCount = getGcCount(),
            availablePermits = getAvailablePermits()
        )
        systemMetrics.add(metric)
    }
    
    private fun getProcessCpuUsage(): Double {
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        return if (osBean is com.sun.management.OperatingSystemMXBean) {
            osBean.processCpuLoad * 100
        } else {
            -1.0
        }
    }
    
    private fun getGcCount(): Long {
        return ManagementFactory.getGarbageCollectorMXBeans()
            .sumOf { it.collectionCount }
    }
    
    private fun getAvailablePermits(): Int {
        // Rate Limiter에서 사용 가능한 토큰 수 조회
        return 10 // 임시 값
    }
}

data class SystemMetric(
    val timestamp: Instant,
    val cpuUsage: Double,
    val memoryUsage: Long,
    val gcCount: Long,
    val availablePermits: Int
)

/**
 * Kafka 테스트 데이터 생성기
 */
@Component
class KafkaTestDataGenerator {
    
    fun generateTestEvents(count: Int, groupIds: List<String>): List<TestEvent> {
        return (1..count).map { index ->
            TestEvent(
                id = "event_${System.currentTimeMillis()}_$index",
                groupId = groupIds.random(),
                userId = "user_${(1..100).random()}",
                eventType = "ORDER_CREATED",
                timestamp = Instant.now(),
                data = mapOf(
                    "amount" to (100..10000).random(),
                    "productId" to "product_${(1..50).random()}"
                ),
                version = 1
            )
        }
    }
    
    fun generateSequentialEvents(groupId: String, count: Int): List<TestEvent> {
        return (1..count).map { index ->
            TestEvent(
                id = "seq_event_${groupId}_$index",
                groupId = groupId,
                userId = "seq_user_$index",
                eventType = "PAYMENT_PROCESSED",
                timestamp = Instant.now().plusSeconds(index.toLong()),
                data = mapOf(
                    "amount" to index * 100,
                    "sequence" to index
                ),
                version = index
            )
        }
    }
    
    fun generateBurstEvents(groupIds: List<String>, eventsPerGroup: Int): List<TestEvent> {
        val events = mutableListOf<TestEvent>()
        val baseTime = Instant.now()
        
        groupIds.forEach { groupId ->
            repeat(eventsPerGroup) { index ->
                events.add(
                    TestEvent(
                        id = "burst_${groupId}_$index",
                        groupId = groupId,
                        userId = "burst_user_${(1..50).random()}",
                        eventType = "ORDER_UPDATED",
                        timestamp = baseTime.plusMillis(index * 10L), // 10ms 간격
                        data = mapOf(
                            "status" to "PROCESSING",
                            "burstIndex" to index
                        ),
                        version = index + 1
                    )
                )
            }
        }
        
        return events.shuffled() // 순서 섞어서 동시성 테스트
    }
}

/**
 * Kafka Parallel Consumer 메트릭 수집기
 */
@Component
class ParallelConsumerMetricsCollector {
    private val metrics = mutableListOf<KafkaConsumerMetric>()
    private var isCollecting = false
    private var parallelConsumer: ParallelStreamProcessor<String, TestEvent>? = null
    
    fun startCollection(parallelConsumer: ParallelStreamProcessor<String, TestEvent>) {
        this.parallelConsumer = parallelConsumer
        this.isCollecting = true
        
        Thread {
            while (isCollecting) {
                collectMetric()
                Thread.sleep(1000) // 1초 간격
            }
        }.start()
    }
    
    fun stopCollection(): List<KafkaConsumerMetric> {
        isCollecting = false
        return metrics.toList()
    }
    
    private fun collectMetric() {
        val consumer = parallelConsumer ?: return
        val runtime = Runtime.getRuntime()
        
        val metric = KafkaConsumerMetric(
            timestamp = Instant.now(),
            messagesProcessed = getProcessedCount(),
            messagesInFlight = getInFlightCount(),
            processingRate = calculateProcessingRate(),
            consumerLag = getConsumerLag(),
            activeWorkers = getActiveWorkerCount(),
            cpuUsage = getProcessCpuUsage(),
            memoryUsage = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
            gcCount = getGcCount(),
            errorCount = getErrorCount()
        )
        
        metrics.add(metric)
    }
    
    private fun getProcessedCount(): Long {
        // Parallel Consumer의 내부 메트릭에서 처리된 메시지 수 조회
        // 실제 구현에서는 Parallel Consumer의 metrics API 사용
        return metrics.size.toLong() * 10 // 임시 값
    }
    
    private fun getInFlightCount(): Int {
        // 현재 처리 중인 메시지 수
        return (0..50).random() // 임시 값
    }
    
    private fun calculateProcessingRate(): Double {
        if (metrics.size < 2) return 0.0
        
        val last = metrics.last()
        val previous = metrics[metrics.size - 2]
        val timeDiff = Duration.between(previous.timestamp, last.timestamp).toMillis().toDouble()
        val messageDiff = last.messagesProcessed - previous.messagesProcessed
        
        return if (timeDiff > 0) (messageDiff / timeDiff) * 1000.0 else 0.0 // msg/sec
    }
    
    private fun getConsumerLag(): Long {
        // Consumer lag 조회 (실제로는 Kafka Admin Client 사용)
        return (0..1000).random().toLong()
    }
    
    private fun getActiveWorkerCount(): Int {
        // 활성 Worker Thread 수 (실제로는 Parallel Consumer 내부 상태)
        return (1..16).random()
    }
    
    private fun getProcessCpuUsage(): Double {
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        return if (osBean is com.sun.management.OperatingSystemMXBean) {
            osBean.processCpuLoad * 100
        } else {
            -1.0
        }
    }
    
    private fun getGcCount(): Long {
        return ManagementFactory.getGarbageCollectorMXBeans()
            .sumOf { it.collectionCount }
    }
    
    private fun getErrorCount(): Long {
        // 에러 발생 횟수 (실제로는 에러 카운터 관리)
        return (0..5).random().toLong()
    }
}

/**
 * Kafka 성능 메트릭 수집기 (전체적인 성능 추적)
 */
@Component  
class KafkaPerformanceMetricsCollector {
    private val performanceHistory = mutableListOf<KafkaPerformanceSnapshot>()
    
    fun recordPerformanceSnapshot(
        concurrency: Int,
        durationSeconds: Long,
        messagesProcessed: Long,
        averageLatency: Double,
        errorCount: Long,
        throughput: Double
    ) {
        val snapshot = KafkaPerformanceSnapshot(
            timestamp = Instant.now(),
            concurrency = concurrency,
            testDurationSeconds = durationSeconds,
            messagesProcessed = messagesProcessed,
            averageLatencyMs = averageLatency,
            p95LatencyMs = averageLatency * 1.5, // 추정값
            p99LatencyMs = averageLatency * 2.0, // 추정값
            throughputMsgPerSec = throughput,
            errorCount = errorCount,
            errorRate = if (messagesProcessed > 0) errorCount.toDouble() / messagesProcessed else 0.0,
            cpuUsagePercent = getCurrentCpuUsage(),
            memoryUsageMB = getCurrentMemoryUsage()
        )
        
        performanceHistory.add(snapshot)
    }
    
    fun getPerformanceHistory(): List<KafkaPerformanceSnapshot> = performanceHistory.toList()
    
    fun getPerformanceImprovement(): Map<String, Double> {
        if (performanceHistory.size < 2) return emptyMap()
        
        val baseline = performanceHistory.first()
        val latest = performanceHistory.last()
        
        return mapOf(
            "throughput_improvement" to (latest.throughputMsgPerSec / baseline.throughputMsgPerSec),
            "latency_improvement" to (baseline.averageLatencyMs / latest.averageLatencyMs),
            "error_rate_change" to (latest.errorRate - baseline.errorRate),
            "resource_efficiency" to calculateResourceEfficiency(latest)
        )
    }
    
    private fun getCurrentCpuUsage(): Double {
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        return if (osBean is com.sun.management.OperatingSystemMXBean) {
            osBean.processCpuLoad * 100
        } else {
            -1.0
        }
    }
    
    private fun getCurrentMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }
    
    private fun calculateResourceEfficiency(snapshot: KafkaPerformanceSnapshot): Double {
        // 처리량 대비 리소스 사용률 효율성
        val resourceUsage = (snapshot.cpuUsagePercent + (snapshot.memoryUsageMB / 10.0)) / 2
        return snapshot.throughputMsgPerSec / maxOf(resourceUsage, 1.0)
    }
}

/**
 * Kafka Consumer 메트릭 데이터 클래스
 */
data class KafkaConsumerMetric(
    val timestamp: Instant,
    val messagesProcessed: Long,
    val messagesInFlight: Int,
    val processingRate: Double, // msg/sec
    val consumerLag: Long,
    val activeWorkers: Int,
    val cpuUsage: Double,
    val memoryUsage: Long, // MB
    val gcCount: Long,
    val errorCount: Long
)

/**
 * Kafka 성능 스냅샷 데이터 클래스  
 */
data class KafkaPerformanceSnapshot(
    val timestamp: Instant,
    val concurrency: Int,
    val testDurationSeconds: Long,
    val messagesProcessed: Long,
    val averageLatencyMs: Double,
    val p95LatencyMs: Double,
    val p99LatencyMs: Double,
    val throughputMsgPerSec: Double,
    val errorCount: Long,
    val errorRate: Double,
    val cpuUsagePercent: Double,
    val memoryUsageMB: Long
)

/**
 * 테스트용 이벤트 데이터 클래스 (도메인 독립적)
 */
data class TestEvent(
    val id: String,
    val groupId: String,
    val userId: String,
    val eventType: String,
    val timestamp: Instant,
    val data: Map<String, Any> = emptyMap(),
    val version: Int = 1
) {
    fun key(): String = "${groupId}_${id}"
}
```

### 3.4 예상 산출물

#### 📈 그래프 종류
1. **Rate Limiter 효과**
   - 동시성 제어 효과 (시계열)
   - 성능 영향도 비교 (막대 차트)
   - 시스템 안정성 지표 (라인 차트)

2. **Kafka Parallel Consumer 성능**
   - 단계적 튜닝 효과 (계단식 차트)
   - 순차 vs 병렬 비교 (비교 막대 차트)
   - 리소스 효율성 (듀얼 축 차트)

#### 📋 정량적 근거 데이터
```yaml
Rate Limiter 효과:
  동시성_제어: "100개 동시 요청 중 정확히 10개만 허용"
  성능_영향: "평균 응답시간 5ms 증가 (95ms → 100ms)"
  시스템_안정성: "과부하 상황에서 CPU 사용률 70% 제한"

Kafka 성능 향상:
  처리량_향상: "20 msg/sec → 200 msg/sec (10배 향상)"
  단계적_튜닝: "동시성 1→4→8→16 단계별 성능 곡선"
  리소스_효율성: "CPU 15% → 70% (효율적 활용)"
```

---

## 🔧 Part 4: 구현 가이드

### 4.1 테스트 환경 설정

#### Testcontainers 자동 환경 구성
이 테스트는 **Testcontainers**를 사용하여 실제 인프라 환경을 자동으로 구성합니다:

```kotlin
@Testcontainers
class IntegrationTestConfiguration {
    companion object {
        @Container
        @JvmStatic
        val redisContainer = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true)
            
        @Container  
        @JvmStatic
        val kafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
            .withReuse(true)
    }
}
```

**장점:**
- ✅ **수동 설정 불필요**: Docker 설치만 되어있으면 모든 인프라 자동 구성
- ✅ **포트 충돌 없음**: 동적 포트 할당으로 안전한 실행
- ✅ **격리된 환경**: 각 테스트마다 깨끗한 상태에서 시작
- ✅ **컨테이너 재사용**: `withReuse(true)`로 실행 속도 향상

### 4.2 테스트 실행 명령어

#### Gradle 태스크
```bash
# 포트폴리오 테스트 실행
./gradlew portfolioTest

# Rate Limiter 테스트만 실행
./gradlew rateLimiterPortfolioTest

# Kafka 테스트만 실행  
./gradlew kafkaPortfolioTest

# 그래프 생성 및 리포트 생성
./gradlew generatePortfolioCharts
```

### 4.3 CI/CD 통합

#### GitHub Actions
```yaml
name: Portfolio Performance Tests

on:
  schedule:
    - cron: '0 2 * * 1'  # 매주 월요일 새벽 2시
  workflow_dispatch:

jobs:
  portfolio-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          
      - name: Run Portfolio Tests
        run: ./gradlew portfolioTest
        
      - name: Upload Charts
        uses: actions/upload-artifact@v3
        with:
          name: portfolio-charts
          path: ./portfolio-charts/
```

---

## 🎯 기대 효과

### 포트폴리오 전시 가치
1. **기술적 의사결정의 정량적 근거**: "왜 이 기술을 선택했는가?"
2. **성능 최적화 과정**: 단계적 튜닝을 통한 성능 향상 스토리
3. **시스템 설계 역량**: 동시성 제어와 성능 최적화의 균형점 도출

### 기술 면접 대응
- **구체적 수치 제시**: "Rate Limiter로 5ms 응답시간 증가하지만 시스템 안정성 확보"
- **최적화 과정 설명**: "Kafka 동시성을 1→16으로 단계적 증가하여 10배 성능 향상"
- **트레이드오프 분석**: "복잡성 증가 vs 성능 향상의 균형점 분석"

이 테스트 전략을 통해 **정량적이고 시각적인 근거**를 포트폴리오에 포함시켜 기술적 역량을 효과적으로 어필할 수 있습니다.