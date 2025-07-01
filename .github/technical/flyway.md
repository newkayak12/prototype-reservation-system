# Flyway
## 📕1. 정의
> - SQL 기반의 이력 관리(버전별 schema, data migration)
> - 개발, 테스트, 운영 DB 간 “스키마 불일치/이력 꼬임” 문제를 원천적으로 해결


## 🎯 2. 목적
1. SQL에 대한 버전 관리
2. CI 단계에서 DB Schema와 JPA Entity 검증
3. CD 단계에서의 DB 변경

## ⚙️ 3. 설정
```yaml
spring:
  flyway:
    user: temporary
    password: temporary
    driver-class-name: com.mysql.cj.jdbc.Driver
    table: flyway_version_control
    baseline-on-migrate: true
    validate-on-migrate: true
    clean-disabled: true
    locations: classpath:db/migration
    url:  jdbc:mysql://localhost:3306/flyway ##Schema
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

## 💥 4. TroubleShooting
- 목표: flyway와 실제 사용하는 db의 schema를 분리하고 싶었다.
- 문제점: 이 둘을 분리하려 했지만 쉽지 않았다.
  - yaml에서 url 부분의 flyway schema를 지정하는 것
  - yaml에서 datasource의 url을 지정하는 것
  - 이 둘을 설정하면 flyway로 덮어쓰는 문제가 있었따.
- 해결: 
  - 이에 따라 flyway는 위와 같이 설정하고
  - resource/migration에 있는 sql에 `CREATE TABLE prototype_reservation.｀user｀`와 같이 명시하여 분리를 완료했다. 
