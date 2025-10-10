rootProject.name = "reservation"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "shared-module",
    "core-module",
    "application-module",
    "adapter-module",
    "test-module",
    "batch-module"
)
