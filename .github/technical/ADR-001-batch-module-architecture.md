# ADR-001: Batch Module Architecture Design

## Status
Accepted

## Context

현재 예약 시스템은 Spring Boot 3.4.5와 Kotlin 2.0.10을 기반으로 Hexagonal Architecture와 Domain-Driven Design(DDD) 원칙을 따라 구현되어 있다. 

기존 모듈 구조:
- `shared-module`: 공통 Enumerations, 예외, 유틸리티
- `core-module`: 도메인 엔티티, 도메인 서비스 (순수 도메인 로직)
- `application-module`: Use Cases, Input/Output Ports
- `adapter-module`: Controllers, Security, Persistence, JPA Entities
- `test-module`: 테스트 유틸리티 및 공통 Fixtures

시스템이 성장하면서 다음과 같은 배치 처리 요구사항이 발생했다:
1. 만료된 예약 정리 (일일 정리 작업)
2. 통계 데이터 집계 (주간/월간 리포트)
3. 외부 시스템과의 데이터 동기화
4. 대용량 데이터 마이그레이션 작업

## Decision

독립적인 `batch-module`을 추가하여 Spring Batch 기반의 배치 처리 시스템을 구축한다.

### Architecture Decision

#### 1. 모듈 구조
```
batch-module/
├── src/main/kotlin/com/reservation/batch/
│   ├── BatchApplication.kt          # 독립 실행 가능한 배치 애플리케이션
│   ├── config/                      # Spring Batch 설정
│   │   ├── BatchConfig.kt
│   │   ├── DatabaseConfig.kt
│   │   └── JobRepositoryConfig.kt
│   ├── job/                         # Job 정의 (Application Layer)
│   │   ├── ReservationCleanupJob.kt
│   │   ├── StatisticsAggregationJob.kt
│   │   └── DataSyncJob.kt
│   ├── step/                        # Step 정의 (Application Layer)
│   │   ├── CleanupStep.kt
│   │   ├── AggregationStep.kt
│   │   └── SyncStep.kt
│   ├── reader/                      # ItemReader (Adapter Layer)
│   │   ├── ExpiredReservationReader.kt
│   │   └── StatisticsDataReader.kt
│   ├── processor/                   # ItemProcessor (Application Layer)
│   │   ├── ReservationCleanupProcessor.kt
│   │   └── StatisticsProcessor.kt
│   ├── writer/                      # ItemWriter (Adapter Layer)
│   │   ├── CleanupResultWriter.kt
│   │   └── StatisticsWriter.kt
│   └── scheduler/                   # Job 스케줄링
│       ├── JobScheduler.kt
│       └── JobLauncher.kt
└── src/main/resources/
    ├── application-batch.yaml
    └── batch-jobs/
        └── job-configurations.yaml
```

#### 2. 의존성 설계
```kotlin
dependencies {
    // 기존 모듈 의존성
    implementation(project(":shared-module"))      // 공통 유틸리티, 예외
    implementation(project(":core-module"))        // 도메인 로직 재사용
    implementation(project(":application-module")) // Use Case 재사용
    
    // Spring Batch
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    
    // 데이터베이스 (기존 설정 재사용)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("mysql:mysql-connector-java")
    implementation("org.flywaydb:flyway-core")
    
    // 테스트
    testImplementation(project(":test-module"))
    testImplementation("org.springframework.batch:spring-batch-test")
}
```

#### 3. 계층별 역할 분담

**Application Layer (job/, step/, processor/)**
- Job과 Step 정의
- 비즈니스 로직 처리 (기존 application-module의 Use Case 재사용)
- 데이터 변환 및 검증

**Adapter Layer (reader/, writer/)**
- 데이터 소스와의 입출력 처리
- 외부 시스템과의 인터페이스

**Domain Layer**
- 기존 core-module의 도메인 로직 재사용
- 새로운 도메인 로직이 필요한 경우 core-module에 추가

#### 4. 실행 모델
- **독립 실행**: `BatchApplication.kt`로 웹 애플리케이션과 분리된 독립 실행
- **스케줄 기반**: Quartz Scheduler를 통한 정기 실행
- **수동 실행**: 관리자가 필요에 따라 수동 실행 가능

## Rationale

### 왜 독립적인 모듈인가?

1. **관심사 분리**: 배치 처리와 웹 애플리케이션의 라이프사이클이 다름
2. **배포 독립성**: 웹 서비스에 영향 없이 배치 작업 배포/수정 가능
3. **리소스 격리**: 배치 작업의 높은 메모리/CPU 사용이 웹 서비스에 영향 주지 않음
4. **확장성**: 배치 전용 서버에서 독립적 스케일링 가능

### 왜 기존 모듈을 재사용하는가?

1. **코드 중복 방지**: 이미 검증된 도메인 로직과 Use Case 재활용
2. **일관성 유지**: 동일한 비즈니스 규칙이 웹과 배치에서 동일하게 적용
3. **유지보수성**: 비즈니스 로직 변경 시 한 곳에서만 수정

### Spring Batch 선택 이유

1. **트랜잭션 관리**: 청크 단위 트랜잭션으로 안정적인 대용량 처리
2. **재시작 가능**: 실패 지점부터 재시작 가능한 내장 메커니즘
3. **모니터링**: JobRepository를 통한 실행 이력 및 상태 관리
4. **Spring 생태계**: 기존 Spring Boot 인프라와 자연스러운 통합

## Consequences

### Positive
- **아키텍처 일관성**: 기존 Hexagonal Architecture 원칙 유지
- **코드 재사용**: application-module과 core-module의 검증된 로직 활용
- **독립적 운영**: 웹 서비스와 독립적인 배포/운영 가능
- **확장성**: 새로운 배치 작업 추가 시 일관된 패턴 적용
- **테스트 용이성**: 각 계층별 단위 테스트 및 통합 테스트 가능

### Negative
- **복잡성 증가**: 추가 모듈로 인한 프로젝트 복잡도 상승
- **설정 관리**: 배치 전용 설정 파일 및 환경 변수 관리 필요
- **리소스 사용**: Spring Batch JobRepository용 추가 데이터베이스 테이블 필요

### Risks and Mitigation

**Risk: 모듈 간 의존성 관리 복잡성**
- Mitigation: 명확한 의존성 규칙 정의 및 문서화

**Risk: 배치 작업 실패 시 대응**
- Mitigation: 철저한 로깅, 알림 시스템, 재시작 메커니즘 구축

**Risk: 데이터 일관성 문제**
- Mitigation: 적절한 트랜잭션 경계 설정 및 Lock 정책 수립

## Implementation Plan

### Phase 1: 기반 구조 구축 (1-2 weeks)
1. batch-module 생성 및 기본 설정
2. Spring Batch 설정 및 JobRepository 구성
3. 기본 Job/Step 템플릿 작성

### Phase 2: 첫 번째 배치 작업 구현 (1-2 weeks)
1. 만료된 예약 정리 Job 구현
2. 테스트 코드 작성
3. 로컬 환경에서 검증

### Phase 3: 프로덕션 준비 (1 week)
1. 운영 환경 설정
2. 모니터링 및 알림 시스템 연동
3. 운영 가이드 작성

## Related Decisions
- ADR-002: Batch Job Monitoring Strategy (예정)
- ADR-003: Data Migration Strategy (예정)

## Notes
- 이 결정은 프로젝트의 현재 아키텍처를 기반으로 하며, 향후 요구사항 변경에 따라 수정될 수 있음
- 배치 모듈의 성능 최적화는 실제 운영 데이터를 기반으로 점진적으로 개선할 예정