package com.reservation.featureflag.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.featureflag.configurations.TestListenRetryReason
import com.reservation.featureflag.configurations.TestRetryConfig
import com.reservation.featureflag.port.input.FindFeatureFlagUseCase
import com.reservation.featureflag.port.input.query.request.FindFeatureFlagQuery
import com.reservation.featureflag.port.output.FindFeatureFlag
import com.reservation.featureflag.port.output.FindFeatureFlag.FindFeatureFlagResult
import com.reservation.featureflag.port.output.SaveFeatureFlag
import com.reservation.featureflag.usecase.FindFeatureFlagServiceTest.InitializeMockContext
import com.reservation.fixture.FixtureMonkeyFactory
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.aop.Advisor
import org.springframework.aop.framework.ProxyFactory
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
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
class FindFeatureFlagServiceTest {
    @Autowired
    @Qualifier("fetchFeatureFlagTemplate")
    private lateinit var fetchFeatureFlagTemplate: FindFeatureFlag

    @Autowired
    @Qualifier("findFeatureFlagRepository")
    private lateinit var findFeatureFlagRepository: FindFeatureFlag

    @Autowired
    private lateinit var saveFeatureFlagTemplate: SaveFeatureFlag

    @Autowired
    private lateinit var service: FindFeatureFlagUseCase

    @TestConfiguration
    class InitializeMockContext {
        @Bean
        fun fetchFeatureFlagTemplate(): FindFeatureFlag = mockk<FindFeatureFlag>()

        @Bean
        fun findFeatureFlagRepository(): FindFeatureFlag = mockk<FindFeatureFlag>()

        @Bean
        fun saveFeatureFlagTemplate(): SaveFeatureFlag = mockk<SaveFeatureFlag>()

        @Bean
        fun findFeatureFlagService(
            fetchFeatureFlagTemplate: FindFeatureFlag,
            findFeatureFlagRepository: FindFeatureFlag,
            saveFeatureFlagTemplate: SaveFeatureFlag,
            retryAdvisor: Advisor,
        ): FindFeatureFlagUseCase {
            val target =
                FindFeatureFlagService(
                    fetchFeatureFlagTemplate = fetchFeatureFlagTemplate,
                    findFeatureFlagRepository = findFeatureFlagRepository,
                    saveFeatureFlagTemplate = saveFeatureFlagTemplate,
                )

            val factory = ProxyFactory(target)
            factory.isProxyTargetClass = true // CGLIB 강제
            factory.addAdvisor(retryAdvisor)
            return factory.proxy as FindFeatureFlagUseCase
        }
    }

    @DisplayName("Proxy Test")
    @Test
    fun contextLoad() {
        val targets =
            listOf(
                AopUtils.isAopProxy(service),
                AopUtils.isJdkDynamicProxy(service),
                AopUtils.isCglibProxy(service),
            )

        assertSoftly {
            assertThat(targets).anyMatch { it }
        }
    }

    @DisplayName("Redis에 feature flag를 조회할 때")
    @Nested
    inner class `Find featureflag from Redis successfully` {
        // 정상 시나리오 - Redis에서 피처 플래그를 성공적으로 조회
        // given: Redis에 피처 플래그 데이터가 존재
        // when: execute 메서드 호출
        // then: Redis에서 조회된 피처 플래그 반환
        @DisplayName("실제 조회할 때")
        @Nested
        inner class `When exectue` {
            @DisplayName("Redis에서 조회된 피처 플래그 반환")
            @Test
            fun `redis return feature flag`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val redisResult = pureMonkey.giveMeOne<FindFeatureFlagResult>()
                val query = pureMonkey.giveMeOne<FindFeatureFlagQuery>()
                every {
                    fetchFeatureFlagTemplate.query(any())
                } returns redisResult

                val serviceResult = service.execute(query)

                serviceResult shouldNotBe null

                verify(exactly = 1) {
                    fetchFeatureFlagTemplate.query(any())
                }
                verify(exactly = 0) {
                    findFeatureFlagRepository.query(any())
                    saveFeatureFlagTemplate.command(any())
                }
            }
        }
    }

    @DisplayName("Redis에 feature flag를 조회할 때")
    @Nested
    inner class `Find featureflag cachemiss so fetch database` {
        // Redis 캐시 미스 시나리오 - Redis에 없어서 DB에서 조회 후 Redis에 저장
        // given: Redis에 피처 플래그 데이터가 없고, DB에는 존재
        // when: execute 메서드 호출
        // then: DB에서 조회한 데이터를 Redis에 저장 후 반환

        @DisplayName("cache miss로 Database 실제 조회하고")
        @Nested
        inner class `When exectue` {
            @DisplayName("Database에서 조회된 피처 플래그 반환")
            @Test
            fun `redis return feature flag`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val databaseResult = pureMonkey.giveMeOne<FindFeatureFlagResult>()
                val query = pureMonkey.giveMeOne<FindFeatureFlagQuery>()

                every {
                    fetchFeatureFlagTemplate.query(any())
                } returns null

                every {
                    saveFeatureFlagTemplate.command(any())
                } returns true

                every {
                    findFeatureFlagRepository.query(any())
                } returns databaseResult

                val serviceResult = service.execute(query)

                serviceResult shouldNotBe null

                verify(exactly = 1) { fetchFeatureFlagTemplate.query(any()) }
                verify(exactly = 1) { saveFeatureFlagTemplate.command(any()) }
                verify(exactly = 1) { findFeatureFlagRepository.query(any()) }
            }
        }
    }

    @DisplayName("Redis에 feature flag를 조회할 때")
    @Nested
    inner class `Find featureflag from redis` {
        // Redis 1회 실패 후 재시도 성공 시나리오
        // given: Redis 1회 실패 후 2회차에 성공
        // when: execute 메서드 호출 (maxAttempts=3, @Retryable 적용)
        // then: 재시도를 통해 Redis에서 성공적으로 조회

        @DisplayName("redis에 요청했으나 redis connection 실패가 발생했고 재시도 한다.")
        @Nested
        inner class `First connection failed so retry` {
            @DisplayName("redis에서 조회된 피처 플래그 반환한다")
            @Test
            fun `redis return feature flag`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val redisResult = pureMonkey.giveMeOne<FindFeatureFlagResult>()
                val query = pureMonkey.giveMeOne<FindFeatureFlagQuery>()

                every {
                    fetchFeatureFlagTemplate.query(any())
                } throws Exception() andThen redisResult

                val serviceResult = service.execute(query)

                serviceResult shouldNotBe null

                verify(exactly = 2) { fetchFeatureFlagTemplate.query(any()) }
                verify(exactly = 0) { saveFeatureFlagTemplate.command(any()) }
                verify(exactly = 0) { findFeatureFlagRepository.query(any()) }
            }
        }

        // Redis 3회 모두 실패 후 @Recover를 통한 DB 조회 시나리오
        // given: Redis 3회 모두 실패, DB에는 데이터 존재
        // when: execute 메서드 호출
        // then: @Recover 메서드가 실행되어 DB에서 조회 성공

        @DisplayName("redis에 요청했으나 redis connection 실패가 발생했고 3회 재시도 후 실패하여")
        @Nested
        inner class `Connection failed with 3 times` {
            @DisplayName("Database에서 조회된 피처 플래그 반환")
            @Test
            fun `database return feature flag`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val databaseResult = pureMonkey.giveMeOne<FindFeatureFlagResult>()
                val query = pureMonkey.giveMeOne<FindFeatureFlagQuery>()

                every {
                    fetchFeatureFlagTemplate.query(any())
                } throwsMany
                    listOf(
                        Exception(),
                        Exception(),
                        Exception(),
                    )

                every {
                    findFeatureFlagRepository.query(any())
                } returns databaseResult

                val serviceResult = service.execute(query)

                serviceResult shouldNotBe null

                verify(exactly = 3) { fetchFeatureFlagTemplate.query(any()) }
                verify(exactly = 0) { saveFeatureFlagTemplate.command(any()) }
                verify(exactly = 1) { findFeatureFlagRepository.query(any()) }
            }
        }
    }

    @DisplayName("feature flag를 조회할 때")
    @Nested
    inner class `Find featureflag` {
        // 모든 저장소 실패 시 failOver 반환 시나리오
        // given: Redis와 DB 모두에서 데이터 조회 실패
        // when: execute 메서드 호출
        // then: failOver 데이터 반환 (id=-1, isEnabled=false)
        @DisplayName("Redis, Database에서 조회했지만 모두 실패해서 ")
        @Nested
        inner class `Failed to fetch from redis and database` {
            private val failOverId = -1L
            private val failOverIsEnabled = false

            @DisplayName("FailOver 값을 반환한다.")
            @Test
            fun `return fail-over value`() {
                val pureMonkey = FixtureMonkeyFactory.giveMePureMonkey().build()
                val query = pureMonkey.giveMeOne<FindFeatureFlagQuery>()

                every {
                    fetchFeatureFlagTemplate.query(any())
                } throwsMany
                    listOf(
                        Exception(),
                        Exception(),
                        Exception(),
                    )

                every {
                    findFeatureFlagRepository.query(any())
                } returns null

                val serviceResult = service.execute(query)

                serviceResult.id shouldBe failOverId
                serviceResult.isEnabled shouldBe failOverIsEnabled

                verify(exactly = 3) { fetchFeatureFlagTemplate.query(any()) }
                verify(exactly = 0) { saveFeatureFlagTemplate.command(any()) }
                verify(exactly = 1) { findFeatureFlagRepository.query(any()) }
            }
        }
    }
}
