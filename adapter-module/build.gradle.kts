configurations.create("asciidoctorExt")


dependencies {
    implementation("io.jsonwebtoken:jjwt:0.12.6")
    implementation ("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    add("asciidoctorExt", "org.springframework.restdocs:spring-restdocs-asciidoctor")
    testImplementation("com.epages:restdocs-api-spec-mockmvc:0.19.4")

    implementation ("com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.10.0")
    runtimeOnly ("com.h2database:h2")
    implementation("it.ozimov:embedded-redis:0.7.2")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(project(":shared-module"))
    implementation(project(":application-module"))
    compileOnly(project(":core-module"))
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.asciidoctor {
    inputs.dir(project.extra["snippetsDir"]!!)
    dependsOn(tasks.test)
}

