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

    fun restDocumentation(testCase: TestCase): ManualRestDocumentation =
        restDocumentationMap[buildKey(testCase)]!!

    private fun buildKey(testCase: TestCase): String =
        "${testCase.spec::class.java.simpleName}-${testCase.name.testName}"

    override suspend fun beforeEach(testCase: TestCase) {
        val testKey = buildKey(testCase)

        val manualRestDocumentation = ManualRestDocumentation(outputDirectory)
        manualRestDocumentation.beforeTest(
            testCase.spec::class.java, // testClass
            testCase.name.testName, // testMethodName
        )

        // 현재 스레드에 ManualRestDocumentation 매핑
        restDocumentationMap[testKey] = manualRestDocumentation
    }

    override suspend fun afterEach(
        testCase: TestCase,
        result: TestResult,
    ) {
        val testKey = buildKey(testCase)
        val manualRestDocumentation = restDocumentationMap[testKey]

        manualRestDocumentation?.let {
            it.afterTest()
            // 테스트 완료 후 정리
            restDocumentationMap.remove(testKey)
        }
    }
}
