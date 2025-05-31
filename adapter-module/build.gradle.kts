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

val apiSpec = file("/api-spec/openapi3.yaml")
val reservation = "reservation"
openapi3 {
    setServer("http://localhost:8080")
    title = reservation
    description = "$reservation-API"
    version = "1.0.0"
    format = "yaml"
}

swaggerSources {
    create(reservation) {
        setInputFile(apiSpec)
    }
}

tasks.register("swagger") {
    dependsOn("openapi3")
    doFirst {
        val swaggerUIFile = file("${project.buildDir}/api-spec/openapi3.yaml")

        val securitySchemesContent = """
            securitySchemes:
              APIKey:
                type: apiKey
                name: Authorization
                `in`: header
            security:
              - APIKey: []  # Apply the security scheme here
        """.trimIndent()

        swaggerUIFile.appendText("\n$securitySchemesContent")
        println("Append Security Settings")
    }

    doLast {
        copy {
            from("${layout.buildDirectory.get()}/api-spec/openapi3.yaml")
            into("${layout.buildDirectory.get()}/resources/main/static/docs")
        }
        println("Copy yaml")
    }
}

dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    add("asciidoctorExt", "org.springframework.restdocs:spring-restdocs-asciidoctor")
    testImplementation("com.epages:restdocs-api-spec-mockmvc:0.19.4")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")
    testImplementation(project(":test-module"))
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
