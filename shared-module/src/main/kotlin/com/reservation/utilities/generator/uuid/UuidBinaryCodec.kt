package com.reservation.utilities.generator.uuid

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private const val BINARY_LENGTH = 16
private val CANONICAL_UUID_PATTERN =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

/**
 * 표준(소문자, 하이픈 포함) UUID 문자열과 16바이트 바이너리 표현 사이를 변환합니다.
 *
 * 바이트 순서는 하이픈을 제거한 문자열을 그대로 16진수로 읽은 것과 같습니다 — 즉 MySQL의
 * UNHEX(REPLACE(uuid, '-', ''))가 만드는 바이트와 정확히 일치합니다. UUID 버전에 따른
 * MSB/LSB 바이트 스왑 여부는 이 codec의 책임이 아니며, 필요하다면 JPA 컨버터나 마이그레이션
 * 쪽에서 별도로 결정합니다.
 */
object UuidBinaryCodec {
    /**
     * 표준 소문자 하이픈 UUID 문자열을 16바이트 배열로 인코딩합니다.
     * @throws IllegalArgumentException [uuid]가 표준 UUID 형식이 아닌 경우
     */
    fun encode(uuid: String): ByteArray {
        require(CANONICAL_UUID_PATTERN.matches(uuid)) {
            "uuid must be a canonical lowercase hyphenated UUID string: $uuid"
        }
        val parsed = UUID.fromString(uuid)
        return ByteBuffer.allocate(BINARY_LENGTH)
            .order(ByteOrder.BIG_ENDIAN)
            .putLong(parsed.mostSignificantBits)
            .putLong(parsed.leastSignificantBits)
            .array()
    }

    /**
     * 16바이트 배열을 표준 소문자 하이픈 UUID 문자열로 디코딩합니다.
     * @throws IllegalArgumentException [bytes]의 크기가 16이 아닌 경우
     */
    fun decode(bytes: ByteArray): String {
        require(bytes.size == BINARY_LENGTH) {
            "bytes must be exactly $BINARY_LENGTH bytes, but was ${bytes.size}"
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        return UUID(buffer.long, buffer.long).toString()
    }
}
