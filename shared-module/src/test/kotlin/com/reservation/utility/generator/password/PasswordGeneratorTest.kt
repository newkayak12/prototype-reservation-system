package com.reservation.utility.generator.password

import com.reservation.utilities.generator.password.PasswordGenerator
import org.hibernate.validator.internal.util.Contracts.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PasswordGeneratorTest {
    @DisplayName("패스워드 정상 생성 확인")
    @Test
    fun `is valid password`() {
        val password = PasswordGenerator.createDefaultPassword()
        /*
         * 패스워드 조건:
         * - 최소 1개의 소문자
         * - 최소 1개의 대문자
         * - 최소 1개의 숫자
         * - 최소 1개의 특수 문자
         * - 길이: 8~18자
         */
        val regExp =
            Regex(
                """
                ^(?=.*[a-z])
                (?=.*[A-Z])
                (?=.*\d)
                (?=.*[~!@#${'$'}%^&*()_+\-={}|\[\]:;"'<>,.?/])
                .{8,18}${'$'}
                """.trimIndent().replace("\n", ""),
            )

        assertTrue(regExp.matches(password), "형식에 불일치합니다.")
    }
}
