# Flyway
## 📕1. 정의
> - SQL 기반의 이력 관리(버전별 schema, data migration)
> - 개발, 테스트, 운영 DB 간 “스키마 불일치/이력 꼬임” 문제를 원천적으로 해결


## 🎯 2. 목적
1. SQL에 대한 버전 관리
2. CI 단계에서 DB Schema와 JPA Entity 검증
3. CD 단계에서의 DB 변경

## 📜 3. 핵심 특성
### ✅ 특성
1. Version 관리: `V1__init.sql`과 같이 버전 기반 마이그레이션 파일을 실행합니다.
2. SchemaHistoryTable 관리: `flyway_schema_history`(default) 테이블에 실행 이력, 성공 여부, 해시 값을 저장합니다.
3. 자동 검증: 실행 시 이전 실행된 마이그레이션과 현재 파일이 일치하는지 검증합니다.
4. Baseline: 마이그레이션을 특정 버전부터 실행할 수 있도록 baseline이 존재합니다.
5. Out-of-order: 기본은 순차지만 out-of-order 설정시 과거 파일 버전도 이후 실해잉 가능합니다.
6. Java 기반 확장 기능: Java 코드 기반 마이그레이션도 가능합니다.
### ❌ 주의
1. Flyway 자체가 멱등성을 보장하지 않습니다.: 단지 history 에 기록하고 여러 번 실행하지 않도록 합니다.
### ↔️ 추가 사항
1. RepeatableMigration (R__*.sql)
   
|특징 |설명 |
|:---:|:---:|
|파일명|R__<name>.sql|
|실행 시점 |파일이 수정되었을 때만 재실행|
|용도 |View, Stored Procedure, Function 등 정의문 반복 적용|
|체크 방식 | 파일의 checksum이 변경되면 재실행됨|
|주의 |INSERT, UPDATE와 같이 상태에 영향을 주는 작업은 반드시 피할 것 (멱등성 보장 불가)|


## ⚙️ 4. 설정
```yaml
flyway:
  enabled: true                             # Flyway 실행 여부 (기본값: true)
  url: jdbc:postgresql://localhost:5432/db  # 데이터베이스 접속 URL
  user: myuser                              # DB 접속 계정
  password: mypassword                      # DB 접속 비밀번호

  locations:                                # 마이그레이션 파일 위치
    - classpath:db/migration                # 기본 위치
    - filesystem:/sql/custom                # 외부 파일 경로도 가능

  schemas:                                  # 마이그레이션 대상 스키마 목록
    - public

  default-schema: public                    # 마이그레이션 대상 기본 스키마

  table: flyway_schema_history              # 메타 정보 저장 테이블명

  baseline-on-migrate: true                 # 기존 DB에 베이스라인 적용 여부
  baseline-version: 1                       # 베이스라인 버전 번호
  baseline-description: "Baseline applied"  # 베이스라인 설명

  validate-on-migrate: true                 # 마이그레이션 전 validation 수행 여부
  clean-disabled: true                      # flyway clean 명령 비활성화 (운영환경 필수) -> Clean 시 전체 스키마를 삭제합니다.
  clean-on-validation-error: false          # validation 실패 시 자동 clean 수행 여부

  group: false                              # 동일 버전 마이그레이션을 하나의 트랜잭션으로 묶을지

  mixed: false                              # SQL과 Java 기반 마이그레이션 혼합 허용 여부
  ignore-missing-migrations: false          # 누락된 마이그레이션 무시 여부
  ignore-future-migrations: false           # 미래 버전의 마이그레이션 무시 여부
  out-of-order: false                       # 버전 순서 무시하고 실행할지 여부

  placeholders:                             # SQL 내 placeholder 변수 치환
    schema: my_schema
    table_prefix: app_

  placeholder-prefix: '${'                  # 플레이스홀더 시작 문자
  placeholder-suffix: '}'                   # 플레이스홀더 종료 문자

  encoding: UTF-8                           # SQL 파일 인코딩
  detect-encoding: true                     # SQL 파일에서 BOM 등을 기반으로 인코딩 감지 여부
  connect-retries: 3                        # 연결 재시도 횟수
  connect-retries-interval: 120             # 연결 재시도 간격 (초)
  fail-on-missing-locations: true           # 마이그레이션 경로 누락 시 실패할지 여부
```
- 위와 같은 형태로 yaml을 설정한다.(test)

```kotlin
@Configuration
class FlywayConfig {
    @Bean
    @FlywayDataSource
    fun flywayDataSource(flyway: FlywayProperties): DataSource =
        DataSourceBuilder.create()
            .url(flyway.url)
            .username(flyway.user)
            .password(flyway.password)
            .driverClassName(flyway.driverClassName)
            .build()
}

```
- 위 yaml에서 읽은 것으로 datasource를 지정한다.

## 💥 5. TroubleShooting
- 목표: flyway와 실제 사용하는 db의 schema를 분리하고 싶었다.
- 문제점: 이 둘을 분리하려 했지만 쉽지 않았다.
  - yaml에서 url 부분의 flyway schema를 지정하는 것
  - yaml에서 datasource의 url을 지정하는 것
  - 이 둘을 설정하면 flyway로 덮어쓰는 문제가 있었따.
- 해결: 
  - 해당 설정을 지원하는 yaml의 프로퍼티가 존재했다. `spring.flway.schemas=flyway`와 같이 설정했다.
  - url 부분의 schema 지정은 해제했다.


