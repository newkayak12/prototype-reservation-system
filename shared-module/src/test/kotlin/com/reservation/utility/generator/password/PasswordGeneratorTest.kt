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
