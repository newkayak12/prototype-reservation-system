package com.reservation.encrypt.aes

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AESUtilityTest {
    companion object {
        @Suppress("MaxLineLength")
        private const val SECRET_KEY =
            "af869782f3abde439def4230f2b05bd701f1ab047ec80808d3b84f1c8923506a"
    }

    private lateinit var aesUtility: AESUtility

    @BeforeEach
    fun setup() {
        aesUtility = AESUtility(SECRET_KEY)
    }

    @Test
    @DisplayName("구현한 AES 알고리즘을 사용한 암복호화 유틸리티로 암호화 -> 복호화를 테스트 한다.")
    fun aesTest() {
        val testTarget = "TEST"
        val encryptedTarget = aesUtility.encrypt(testTarget)
        val decryptedTarget = aesUtility.decrypt(encryptedTarget)

        assertThat(encryptedTarget).isNotNull()
        assertEquals(testTarget, decryptedTarget)
    }
}
