package com.reservation.config

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.springframework.restdocs.ManualRestDocumentation

/**
 * Spring REST Docs를 위한 Kotest 확장
 * 동시성 안전성을 위해 각 테스트마다 별도 인스턴스를 사용해야 함
 */
class SpringRestDocsKotestExtension(
    private val outputDirectory: String = "build/generated-snippets",
) : TestListener {
    private lateinit var manualRestDocumentation: ManualRestDocumentation

    val restDocumentation: ManualRestDocumentation
        get() = manualRestDocumentation

    override suspend fun beforeEach(testCase: TestCase) {
        manualRestDocumentation = ManualRestDocumentation(outputDirectory)
        manualRestDocumentation.beforeTest(
            testCase.spec::class.java, // testClass
            testCase.name.testName, // testMethodName
        )
    }

    override suspend fun afterEach(
        testCase: TestCase,
        result: TestResult,
    ) {
        manualRestDocumentation.afterTest()
    }
}
