package com.reservation.config

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.restdocs.RestDocumentationExtension

class SpringRestDocsKotestExtension(private val outputDirectory: String) : TestListener {
    private lateinit var restDocumentation: RestDocumentationExtension

    override suspend fun beforeContainer(testCase: TestCase) {
//        restDocumentation = JUnit5RestDocumentation(outputDirectory)
        // Simulate ExtensionContext for beforeEach
        val extensionContext = mockk<ExtensionContext>()
        every { extensionContext.getRequiredTestInstance() } returns testCase.spec
        restDocumentation.beforeEach(extensionContext)
    }

    override suspend fun afterContainer(
        testCase: TestCase,
        result: TestResult,
    ) {
        // Simulate ExtensionContext for afterEach
        val extensionContext = mockk<ExtensionContext>()
        every { extensionContext.getRequiredTestInstance() } returns testCase.spec
        restDocumentation.afterEach(extensionContext)
    }
}
