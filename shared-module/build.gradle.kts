configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-test")
}
dependencies {
    implementation ("com.fasterxml.uuid:java-uuid-generator:4.3.0")
    implementation ("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation ("jakarta.validation:jakarta.validation-api:3.0.2")
}