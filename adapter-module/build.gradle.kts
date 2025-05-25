import io.gitlab.arturbosch.detekt.Detekt

configurations.create("asciidoctorExt")

flyway {
    url = "jdbc:mysql://localhost:3306/flyway"
    user = "temporary"
    password = "temporary"
    driver = "com.mysql.cj.jdbc.Driver"
}

kapt {
    arguments {
        arg("querydsl.generatedAnnotation", "javax.annotation.Generated")
    }
}

sourceSets {
    main {
        java {
            srcDirs("build/generated/kapt/main")
        }
    }
}

tasks.named<Detekt>("detekt") {
    reports {
        html.required.set(true)
        html.outputLocation.set(file("$rootDir/build/reports/${project.name}detekt/detekt.html"))
        sarif.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
    }
}

dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    add("asciidoctorExt", "org.springframework.restdocs:spring-restdocs-asciidoctor")
    testImplementation("com.epages:restdocs-api-spec-mockmvc:0.19.4")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
}

dependencies {
    implementation("com.github.gavlyukovskiy:p6spy-spring-boot-starter:1.10.0")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    runtimeOnly("com.h2database:h2")
}

dependencies {
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
