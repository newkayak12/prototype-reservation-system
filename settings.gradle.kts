rootProject.name = "reservation"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    // --- V1 (Strangler: 전환 완료 시까지 유지) ---
    "shared-module",
    "core-module",
    "application-module",
    "infrastructure-module",
    "adapter-module",
    "test-module",
    "batch-module",

    // --- V2 (신규 뼈대: Phase 7) ---
    "contract-module",
    "command-module:command-core",
    "command-module:command-application",
    "command-module:command-adapter",
    "command-module:command-infrastructure",
    "query-module",
    "auth-server-module",
)
