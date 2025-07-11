# Java Persistence API

## 1. Open
### 1. 문제점
1. JPA Entity는 런타임에 Proxy로 생성해서 사용한다.
2. Kotlin은 클래스, 메소드 기본이 final이다.
3. 이 둘은 상충하는 문제다.
### 2. 해결
```kotlin
kotlin("plugin.spring") version "2.0.10" apply false
kotlin("plugin.jpa") version "2.0.10" apply false
```
- 위 플러그인으로 `@Entity`의 경우 open 시켜서 처리를 했다.


## 2. val? var?
### 1. 문제점
1. JPA는 지연로딩, 더티체킹을 사용하기 위해서 `mutable`해야 한다.
2. Kotlin은 기본적으로 `immutable`함을 지향한다.
3. 이 둘은 상충되는 개념이다.
### 2. 해결
1. JPAEntity는 var로 둔다.
2. 대신 실질적으로 도메인 로직, 비즈니스 로직을 다루는 DomainEntity를 val로 두고 사용한다.

## 3. 빈 생성자
### 1. 문제점
1. JPA는 리플렉션을 바탕으로 동작한다.
2. 리플렉션을 통해서 주입하기 위해서는 빈 생성자가 필요하다.
3. kotlin은 PrimaryConstructor를 통해서 프로퍼티를 표현하는 경우가 있다.

### 2. 해결
```kotlin
kotlin("plugin.jpa") version "2.0.10" apply false
```
1. 위와 같은 플러그인으로 빈 생성자를 암묵적으로 제공받는다.
2. primaryConstructor를 두고 보조 생성자로 정의한다.

----
## +⍺ JPA와 Kotlin 어울리는건가요?
- 솔직히 위 두 문제만으로 JPA와 Kotlin은 궁합이 안맞는게 아닌가? 라는 생각이 들었다.
- 이런 상황에서 `Expose`나 이전에 사용하던 `jooq`를 사용하는 게 어떤가 하는 의문이 들었다.