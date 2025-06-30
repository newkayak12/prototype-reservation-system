# Git Pre-commit Hook

> - pre-commit hook은 Git이 commit 명령을 실행하기 직전에 자동으로 실행하는 사용자 정의 스크립트입니다.
> - .git/hooks/pre-commit 경로에 위치한 실행 파일(스크립트)로, 주로 코드 품질 검사, 테스트 자동화, 포매팅 등에 사용됩니다.

## 1. ⚠️ 설정 

```kotlin
// precommit에 적용할 내용 
fun String.runCommand(): String =
    ProcessBuilder(*split(" ").toTypedArray())
        .redirectErrorStream(true)
        .start()
        .inputStream
        .bufferedReader()
        .readText()

tasks.register<Exec>("preCommitApplySpotless") {
    commandLine("bash", "./gradlew", "spotlessKotlinApply")
}

tasks.register<Exec>("preCommitDetekt") {
    commandLine("bash", "./gradlew", "detekt")
    mustRunAfter("preCommitApplySpotless")
}

tasks.register<Exec>("preCommitAddCommit") {
    val stagedFiles = "git diff --cached --name-only"
        .runCommand()
        .lines()
        .filter { it.isNotBlank() && File(project.rootDir, it).exists() }
    if (stagedFiles.isNotEmpty()) {
        commandLine(listOf("git", "add") + stagedFiles)
        //stage 된 것만 git add
    } else {
        commandLine("echo", "No staged files to add.")
    }
    mustRunAfter("preCommitDetekt")
}

tasks.register("gitPreCommitHook") {
    dependsOn("preCommitApplySpotless", "preCommitDetekt", "preCommitAddCommit")
    doLast {
        println("Running spotlessKotlinGradleApply and detekt before commit...")
    }
}

// pre-commit 후크 설정
tasks.named("gitPreCommitHook") {
    doLast {
        val hookFile = file(".git/hooks/pre-commit")
        if (!hookFile.exists()) {
            hookFile.writeText(
                """
                #!/bin/sh
                ./gradlew gitPreCommitHook
                """.trimIndent(),
            )
            hookFile.setExecutable(true)
            println("Git pre-commit hook set up successfully!")
        }
    }
}
```

- 반드시 최초 1회 이상 gitPreCommitHook을 실행시켜서 shell을 등록한다.
- 이후 commit은 위의 명시된 동작을 진행한다.