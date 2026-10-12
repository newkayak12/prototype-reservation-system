package com.reservation.utility.generator.uuid

import com.reservation.utilities.generator.uuid.UuidBinaryCodec
import com.reservation.utilities.generator.uuid.UuidGenerator
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UuidBinaryCodecTest {
    companion object {
        // RFC 4122 example UUID, chosen only as a fixed, well-known golden vector.
        private const val GOLDEN_UUID = "123e4567-e89b-12d3-a456-426614174000"

        // Literal 16 bytes matching MySQL's UNHEX(REPLACE(GOLDEN_UUID, '-', '')):
        // 12 3e 45 67 e8 9b 12 d3 a4 56 42 66 14 17 40 00
        private val GOLDEN_BYTES =
            byteArrayOf(
                0x12,
                0x3e,
                0x45,
                0x67,
                0xe8.toByte(),
                0x9b.toByte(),
                0x12,
                0xd3.toByte(),
                0xa4.toByte(),
                0x56,
                0x42,
                0x66,
                0x14,
                0x17,
                0x40,
                0x00,
            )

        private const val ROUND_TRIP_ITERATIONS = 10_000
        private const val EXPECTED_BINARY_SIZE = 16
    }

    @DisplayName("golden vector: encode(text) produces the exact UNHEX(REPLACE(uuid,'-','')) bytes")
    @Test
    fun `encode produces the exact golden bytes`() {
        assertThat(UuidBinaryCodec.encode(GOLDEN_UUID)).isEqualTo(GOLDEN_BYTES)
    }

    @DisplayName("golden vector: decode(bytes) produces the exact canonical text")
    @Test
    fun `decode produces the exact golden text`() {
        assertThat(UuidBinaryCodec.decode(GOLDEN_BYTES)).isEqualTo(GOLDEN_UUID)
    }

    @DisplayName("10000개 생성된 UUID에 대해 encode-decode 라운드트립이 보존된다")
    @Test
    fun `round trips 10000 generated uuids`() {
        repeat(ROUND_TRIP_ITERATIONS) {
            val generated = UuidGenerator.generate()

            val encoded = UuidBinaryCodec.encode(generated)
            assertThat(encoded.size).isEqualTo(EXPECTED_BINARY_SIZE)
            assertThat(UuidBinaryCodec.decode(encoded)).isEqualTo(generated)

            val hyphenStrippedUpper = generated.replace("-", "").uppercase()
            val encodedAsUpperHex =
                encoded.joinToString("") { byte -> "%02X".format(byte) }
            assertThat(encodedAsUpperHex).isEqualTo(hyphenStrippedUpper)
        }
    }

    @DisplayName("빈 문자열은 예외를 던진다")
    @Test
    fun `encode throws on empty string`() {
        assertThatThrownBy { UuidBinaryCodec.encode("") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @DisplayName("35자리 문자열은 예외를 던진다")
    @Test
    fun `encode throws on 35 character string`() {
        val tooShort = GOLDEN_UUID.substring(0, GOLDEN_UUID.length - 1)

        assertThatThrownBy { UuidBinaryCodec.encode(tooShort) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @DisplayName("36자리이지만 16진수가 아닌 문자가 포함되면 예외를 던진다")
    @Test
    fun `encode throws on 36 character string with a non-hex character`() {
        val withNonHexChar = "g23e4567-e89b-12d3-a456-426614174000"

        assertThat(withNonHexChar.length).isEqualTo(GOLDEN_UUID.length)
        assertThatThrownBy { UuidBinaryCodec.encode(withNonHexChar) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @DisplayName("크기가 16이 아닌 ByteArray는 예외를 던진다")
    @Test
    fun `decode throws when byte array size is not 16`() {
        assertThatThrownBy { UuidBinaryCodec.decode(ByteArray(15)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { UuidBinaryCodec.decode(ByteArray(17)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
