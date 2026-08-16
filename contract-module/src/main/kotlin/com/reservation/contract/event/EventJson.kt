package com.reservation.contract.event

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass

/**
 * 통합 이벤트(예: [com.reservation.contract.event.timetable.SeatHeld]) 를 JSON으로 인코딩/디코딩하는
 * 무상태 헬퍼. `eventType` 태그 문자열 → 클래스 복원 레지스트리는 두지 않는다 — 역직렬화 대상 타입은
 * 항상 호출자가 명시한다(DESIGN-019 §6: 복원은 command-application 소관).
 *
 * Jackson 타입은 이 객체의 public 함수 시그니처에 노출하지 않는다 — contract-module은 `java` 플러그인
 * 기반이라 `implementation` 의존이 소비자 컴파일 클래스패스로 전파되지 않는다.
 */
object EventJson {
    private val mapper =
        jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }

    fun encode(event: Any): String = mapper.writeValueAsString(event)

    fun <T : Any> decode(
        json: String,
        type: KClass<T>,
    ): T = mapper.readValue(json, type.java)

    inline fun <reified T : Any> decode(json: String): T = decode(json, T::class)
}
