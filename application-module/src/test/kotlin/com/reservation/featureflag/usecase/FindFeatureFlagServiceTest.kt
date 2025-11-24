package com.reservation.featureflag.usecase

import com.navercorp.fixturemonkey.kotlin.giveMeOne
import com.reservation.featureflag.port.input.query.request.FindFeatureFlagQuery
import com.reservation.featureflag.port.output.FindFeatureFlag
import com.reservation.featureflag.port.output.FindFeatureFlag.FindFeatureFlagResult
import com.reservation.featureflag.port.output.SaveFeatureFlag
import com.reservation.fixture.FixtureMonkeyFactory
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(value = [MockKExtension::class])
class FindFeatureFlagServiceTest {
    @MockK
    private lateinit var fetchFeatureFlagTemplate: FindFeatureFlag

    @MockK
    private lateinit var findFeatureFlagRepository: FindFeatureFlag

    @MockK
    private lateinit var saveFeatureFlagTemplate: SaveFeatureFlag

    @InjectMockKs
    private lateinit var service: FindFeatureFlagService

    @DisplayName("Redis에 feature flag를 조회할 때")
    @Nested
    inner class `Find featureflag from Redis successfully` {
        // 정상 시나리오 - Redis에서 피처 플래그를 성공적으로 조회
        // given: Redis에 피처 플래그 데이터가 존재
        // when: execute 메서드 호출
        // then: Redis에서 조회된 피처 플래그 반환
        @DisplayName("실제 조회할 때")
        @Nested
        inner class `When execute` {
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
                } returns null

                every {
                    saveFeatureFlagTemplate.command(any())
                } returns true

                every {
                    findFeatureFlagRepository.query(any())
                } returns null

                val serviceResult = service.execute(query)

                serviceResult.id shouldBe failOverId
                serviceResult.isEnabled shouldBe failOverIsEnabled

                verify(exactly = 1) { fetchFeatureFlagTemplate.query(any()) }
                verify(exactly = 1) { saveFeatureFlagTemplate.command(any()) }
                verify(exactly = 1) { findFeatureFlagRepository.query(any()) }
            }
        }
    }
}
