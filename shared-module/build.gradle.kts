import io.gitlab.arturbosch.detekt.Detekt

tasks.named<Detekt>("detekt") {
    reports {
        html.required.set(true)
        html.outputLocation.set(file("$rootDir/build/reports/${project.name}detekt/detekt.html"))
        sarif.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
    }
}

configurations.all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-web")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa")
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-test")
}
dependencies {
    implementation("com.fasterxml.uuid:java-uuid-generator:4.3.0")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
}
