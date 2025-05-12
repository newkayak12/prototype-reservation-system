
dependencies {
    implementation(project(":shared-module"))
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
