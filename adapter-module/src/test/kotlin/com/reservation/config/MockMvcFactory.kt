package com.reservation.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.concurrent.ConcurrentHashMap

/**
 * 병렬 테스트 안전 MockMvc Factory
 * - ConcurrentHashMap 기반 캐싱으로 동시성 이슈 해결
 * - Thread-safe 컴포넌트 재사용으로 성능 최적화
 * - 병렬 테스트 실행에서 안전한 MockMvc 생성
 */
object MockMvcFactory {
    // 컨트롤러별 MockMvc 캐싱을 위한 ConcurrentHashMap
    private val mockMvcCache = ConcurrentHashMap<String, MockMvc>()
    private val mockMvcWithDocsCache = ConcurrentHashMap<Pair<String, String>, MockMvc>()

    /**
     * ObjectMapper 제공 - 직접 사용 권장 (thread-safe)
     * 복사가 필요한 특별한 경우만 copyObjectMapper() 사용
     */
    val objectMapper: ObjectMapper
        get() = MockMvcComponent.copyObjectMapper()

    /**
     * 기본 StandaloneMockMvcBuilder 생성
     * - 모든 공통 컴포넌트 설정
     * - Thread-safe 컴포넌트들 재사용
     */
    private fun <T> getStandAloneSetup(controller: T) =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(MockMvcComponent.converter)
            .setValidator(MockMvcComponent.springValidator)
            .setControllerAdvice(MockMvcComponent.restControllerExceptionHandler)

    /**
     * 기본 MockMvc 생성 (RestDocs 없음)
     * - ConcurrentHashMap으로 캐싱하여 동시성 이슈 방지
     */
    fun <T> buildMockMvc(controller: T): MockMvc {
        val controllerKey = "${controller!!::class::simpleName.name}@${controller.hashCode()}"
        return mockMvcCache.computeIfAbsent(controllerKey) {
            getStandAloneSetup(controller).build()
        }
    }

    /**
     * RestDocs가 포함된 MockMvc 생성
     * - ConcurrentHashMap으로 캐싱하여 동시성 이슈 방지
     */
    fun <T> buildMockMvc(
        controller: T,
        restDocumentation: ManualRestDocumentation,
    ): MockMvc {
        val controllerKey = "${controller!!::class::simpleName.name}@${controller.hashCode()}"
        val docsKey = restDocumentation.toString()
        val cacheKey = Pair(controllerKey, docsKey)

        return mockMvcWithDocsCache.computeIfAbsent(cacheKey) {
            getStandAloneSetup(controller)
                .apply<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(
                    MockMvcRestDocumentation.documentationConfiguration(restDocumentation),
                ).build()
        }
    }

    /**
     * 캐시 초기화 - 테스트 간 격리가 필요한 경우 사용
     */
    fun clearCache() {
        mockMvcCache.clear()
        mockMvcWithDocsCache.clear()
    }

    /**
     * 특정 컨트롤러의 캐시만 제거
     */
    fun <T> clearCache(controller: T) {
        val controllerKey = controller!!::class.java.name
        mockMvcCache.remove(controllerKey)
        mockMvcWithDocsCache.entries.removeIf { it.key.first == controllerKey }
    }
}
