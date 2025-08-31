package com.reservation.config

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.springframework.restdocs.ManualRestDocumentation
import java.util.concurrent.ConcurrentHashMap

/**
 * Spring REST Docs를 위한 Kotest 확장
 * ConcurrentHashMap으로 테스트별 독립적인 ManualRestDocumentation 관리하여 동시성 이슈 해결
 */
class SpringRestDocsKotestExtension(
    private val outputDirectory: String = "build/generated-snippets",
) : TestListener {
    // 테스트별로 독립적인 ManualRestDocumentation을 관리하는 ConcurrentHashMap
    private val restDocumentationMap = ConcurrentHashMap<String, ManualRestDocumentation>()

    val restDocumentation: ManualRestDocumentation
        get() {
            val currentThreadName = Thread.currentThread().name
            return restDocumentationMap[currentThreadName]
                ?: throw IllegalStateException(
                    "ManualRestDocumentation not initialized for thread: $currentThreadName",
                )
        }

    override suspend fun beforeEach(testCase: TestCase) {
        val currentThreadName = Thread.currentThread().name
        val testKey = "${testCase.spec::class.java.simpleName}-${testCase.name.testName}"

        val manualRestDocumentation = ManualRestDocumentation(outputDirectory)
        manualRestDocumentation.beforeTest(
            testCase.spec::class.java, // testClass
            testCase.name.testName, // testMethodName
        )

        // 현재 스레드에 ManualRestDocumentation 매핑
        restDocumentationMap[currentThreadName] = manualRestDocumentation
    }

    override suspend fun afterEach(
        testCase: TestCase,
        result: TestResult,
    ) {
        val currentThreadName = Thread.currentThread().name
        val manualRestDocumentation = restDocumentationMap[currentThreadName]

        manualRestDocumentation?.let {
            it.afterTest()
            // 테스트 완료 후 정리
            restDocumentationMap.remove(currentThreadName)
        }
    }
}
