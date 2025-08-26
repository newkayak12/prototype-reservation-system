package com.reservation.config

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import org.springframework.restdocs.ManualRestDocumentation

class SpringRestDocsKotestExtension(
    private val outputDirectory: String = "build/generated-snippets",
) : TestListener {
    lateinit var restDocumentation: ManualRestDocumentation
        protected set

    override suspend fun beforeEach(testCase: TestCase) {
        restDocumentation = ManualRestDocumentation(outputDirectory)
        restDocumentation.beforeTest(
            testCase.spec::class.java, // testClass
            testCase.name.testName, // testMethodName
        )
    }

    override suspend fun afterEach(
        testCase: TestCase,
        result: TestResult,
    ) {
        restDocumentation.afterTest()
    }
}
