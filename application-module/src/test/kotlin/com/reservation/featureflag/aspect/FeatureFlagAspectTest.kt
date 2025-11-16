package com.reservation.featureflag.aspect

import com.navercorp.fixturemonkey.kotlin.giveMeBuilder
import com.reservation.common.exceptions.AccessNotPermittedException
import com.reservation.config.annotations.FeatureFlag
import com.reservation.config.aspect.FeatureFlagAspect
import com.reservation.featureflag.aspect.FeatureFlagAspectTest.InitializeMockContext
import com.reservation.featureflag.configurations.TestListenRetryReason
import com.reservation.featureflag.configurations.TestRetryConfig
import com.reservation.featureflag.port.input.FindFeatureFlagUseCase
import com.reservation.featureflag.port.input.query.response.FindFeatureFlagQueryResult
import com.reservation.fixture.FixtureMonkeyFactory
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(value = [SpringExtension::class])
@ContextConfiguration(
    classes = [
        TestListenRetryReason::class,
        TestRetryConfig::class,
        InitializeMockContext::class,
    ],
)
class FeatureFlagAspectTest {
    companion object {
        private const val FEATURE_FLAG_WITH_NO_FALLBACK = "feature-flag-with-no-fallback"
        private const val FEATURE_FLAG_WITH_FALLBACK = "feature-flag-with-fallback"
        private const val FALLBACK_METHOD_NAME = "fallbackMethod"
        private const val MOCK_FALLBACK_METHOD_NAME = "mockFallbackMethod"
        private const val FALLBACK_SUCCEED_MESSAGE = "fall back succeed"
        private const val ORIGINAL_SUCCEED_MESSAGE = "origin succeed"
    }

    @TestConfiguration
    @EnableAspectJAutoProxy
    class InitializeMockContext {
        @Bean
        fun findFeatureFlagService(): FindFeatureFlagUseCase = mockk<FindFeatureFlagUseCase>()

        @Bean
        fun featureFlagAspect(): FeatureFlagAspect = FeatureFlagAspect(findFeatureFlagService())

        @Bean
        fun testUseCase() = TestUseCase()
    }

    open class TestUseCase {
        @FeatureFlag(featureFlagKey = FEATURE_FLAG_WITH_NO_FALLBACK)
        open fun methodWithNoFallback(): String = ORIGINAL_SUCCEED_MESSAGE

        @FeatureFlag(
            featureFlagKey = FEATURE_FLAG_WITH_FALLBACK,
            fallback = FALLBACK_METHOD_NAME,
        )
        open fun methodWithFallback(): String = ORIGINAL_SUCCEED_MESSAGE

        @FeatureFlag(
            featureFlagKey = FEATURE_FLAG_WITH_FALLBACK,
            fallback = MOCK_FALLBACK_METHOD_NAME,
        )
        open fun methodWithFallbackButNotDeclared(): String = ORIGINAL_SUCCEED_MESSAGE

        open fun fallbackMethod(): String = FALLBACK_SUCCEED_MESSAGE
    }

    @Autowired
    private lateinit var service: TestUseCase

    @Autowired
    private lateinit var findFeatureFlagUseCase: FindFeatureFlagUseCase

    @Test
    fun contextLoad() {
        val targets =
            listOf(
                AopUtils.isAopProxy(service),
                AopUtils.isJdkDynamicProxy(service),
                AopUtils.isCglibProxy(service),
            )

        println(targets)
        assertSoftly {
            assertThat(targets).anyMatch { it }
        }
    }

    // FeatureFlag가 true
    @DisplayName("FeatureFlag가 true")
    @Nested
    inner class `FeatureFlagIsTrue` {
        @DisplayName("메소드를 호출하면 ")
        @Nested
        inner class `Call Method` {
            // 1. feature flag가 활성화된 경우 원본 메서드 실행
            // Given: feature flag enabled
            // When: call annotated method
            // Then: execute original method and return original result

            @DisplayName("메소드의 리턴 값 반환 - methodWithNoFallback")
            @Test
            fun `return original method value - methodWithNoFallback`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val enabledFeatureFlag =
                    pureMonkey.giveMeBuilder<FindFeatureFlagQueryResult>()
                        .set("isEnabled", true)
                        .sample()

                every {
                    findFeatureFlagUseCase.execute(any())
                } returns enabledFeatureFlag

                val resultMethodWithNoFallback = service.methodWithNoFallback()

                resultMethodWithNoFallback shouldBe ORIGINAL_SUCCEED_MESSAGE
            }

            // 2. feature flag가 활성화된 경우 fallback 무시하고 원본 실행
            // Given: feature flag enabled with fallback defined
            // When: call method with fallback annotation
            // Then: execute original method ignoring fallback

            @DisplayName("메소드의 리턴 값 반환 - methodWithFallback")
            @Test
            fun `return original method value - methodWithFallback`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val enabledFeatureFlag =
                    pureMonkey.giveMeBuilder<FindFeatureFlagQueryResult>()
                        .set("isEnabled", true)
                        .sample()

                every {
                    findFeatureFlagUseCase.execute(any())
                } returns enabledFeatureFlag

                val resultMethodWithFallback = service.methodWithFallback()

                resultMethodWithFallback shouldBe ORIGINAL_SUCCEED_MESSAGE
            }

            @DisplayName("메소드의 리턴 값 반환 - methodWithFallbackButNotDeclared")
            @Test
            fun `return original method value - methodWithFallbackButNotDeclared`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val enabledFeatureFlag =
                    pureMonkey.giveMeBuilder<FindFeatureFlagQueryResult>()
                        .set("isEnabled", true)
                        .sample()

                every {
                    findFeatureFlagUseCase.execute(any())
                } returns enabledFeatureFlag

                val resultMethodWithFallbackButNotDeclared =
                    service.methodWithFallbackButNotDeclared()

                resultMethodWithFallbackButNotDeclared shouldBe ORIGINAL_SUCCEED_MESSAGE
            }
        }
    }

    // FeatureFlag가 false
    @DisplayName("FeatureFlag가 false")
    @Nested
    inner class `FeatureFlagIsFalse` {
        // 3. feature flag 비활성화 + 유효한 fallback → fallback 메서드 실행
        // Given: feature flag disabled and valid fallback method exists
        // When: call annotated method
        // Then: execute fallback method and return fallback result
        @DisplayName("유효한 fallback이 있으면")
        @Nested
        inner class `Fallback method exists` {
            @DisplayName("fallback이 실행되고 fallback의 리턴 값이 반환된다.")
            @Test
            fun `run fallback method`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val enabledFeatureFlag =
                    pureMonkey.giveMeBuilder<FindFeatureFlagQueryResult>()
                        .set("isEnabled", false)
                        .sample()

                every {
                    findFeatureFlagUseCase.execute(any())
                } returns enabledFeatureFlag

                val result = service.methodWithFallback()

                result shouldBe FALLBACK_SUCCEED_MESSAGE
            }
        }

        // 4. feature flag 비활성화 + fallback 없음 → AccessNotPermittedException
        // Given: feature flag disabled and no fallback defined
        // When: call annotated method
        // Then: throw AccessNotPermittedException

        @DisplayName("유효한 fallback이 없으면")
        @Nested
        inner class `Fallback method not exists` {
            @DisplayName("fallback이 실행되고 AccessNotPermittedException이 throw된다.")
            @Test
            fun `run fallback method`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val enabledFeatureFlag =
                    pureMonkey.giveMeBuilder<FindFeatureFlagQueryResult>()
                        .set("isEnabled", false)
                        .sample()

                every {
                    findFeatureFlagUseCase.execute(any())
                } returns enabledFeatureFlag

                shouldThrow<AccessNotPermittedException> {
                    service.methodWithNoFallback()
                }
            }
        }

        // 5. feature flag 비활성화 + 존재하지 않는 fallback 메서드 → AccessNotPermittedException
        // Given: feature flag disabled and fallback method doesn't exist
        // When: call method with non-existent fallback
        // Then: throw AccessNotPermittedException

        @DisplayName("유효한 fallback이 선언되어 있지 않으면")
        @Nested
        inner class `Fallback method not decalred` {
            @DisplayName("fallback이 실행되고 AccessNotPermittedException이 throw된다.")
            @Test
            fun `run fallback method`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val enabledFeatureFlag =
                    pureMonkey.giveMeBuilder<FindFeatureFlagQueryResult>()
                        .set("isEnabled", false)
                        .sample()

                every {
                    findFeatureFlagUseCase.execute(any())
                } returns enabledFeatureFlag

                shouldThrow<AccessNotPermittedException> {
                    service.methodWithFallbackButNotDeclared()
                }
            }
        }
    }
}
