package com.reservation.persistence.converter

import com.reservation.utilities.generator.uuid.UuidBinaryCodec
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * 영속성 경계에서만 UUID 식별자 문자열 ↔ BINARY(16)을 변환한다(ADR-028 결정 1).
 * 바이트 변환 로직은 갖지 않고 `shared-module`의 [UuidBinaryCodec]에 전부 위임한다 —
 * 이 클래스는 그 codec을 JPA가 쓸 수 있게 감싸는 어댑터일 뿐이다.
 *
 * `autoApply`는 켜지 않는다 — `@Id`/`@EmbeddedId` 구성요소에서는 암묵 적용이 명세상
 * 보증되지 않으므로(ADR-028), 적용 대상마다 `@Convert`로 명시 선언한다.
 */
@Converter
class UuidBinaryConverter : AttributeConverter<String, ByteArray> {
    override fun convertToDatabaseColumn(attribute: String?): ByteArray? {
        return attribute?.let { UuidBinaryCodec.encode(it) }
    }

    override fun convertToEntityAttribute(dbData: ByteArray?): String? {
        return dbData?.let { UuidBinaryCodec.decode(it) }
    }
}
