package com.reservation.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.restdocs.ManualRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * 병렬 테스트 안전 MockMvc Factory
 * - Thread-safe 컴포넌트 재사용으로 성능 최적화
 * - 병렬 테스트 실행에서 안전한 MockMvc 생성
 */
object MockMvcFactory {
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
     */
    fun <T> buildMockMvc(controller: T): MockMvc = getStandAloneSetup(controller).build()

    /**
     * RestDocs가 포함된 MockMvc 생성
     */
    fun <T> buildMockMvc(
        controller: T,
        restDocumentation: ManualRestDocumentation,
    ): MockMvc =
        getStandAloneSetup(controller)
            .apply<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(
                MockMvcRestDocumentation.documentationConfiguration(restDocumentation),
            ).build()
}
