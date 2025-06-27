package com.reservation.user.policy.validations

/**
 * 비밀번호 복잡도에 대한 정책입니다. 영문, 대소문자 숫자, 특수문자를 포함하여 8 ~ 18입니다.
 */
class PasswordComplexityValidationPolicy : PasswordValidationPolicy {
    companion object {
        @Suppress("MaxLineLength")
        val PASSWORD_COMPLEXITY_REG_EXP =
            Regex(
                """
                ^(?=.*[a-z])
                (?=.*[A-Z])
                (?=.*\d)
                (?=.*[~!@#${'$'}%^&*()_+\-={}|\[\]:;"'<>,.?/])
                .{8,18}${'$'}
                """.trimIndent().replace("\n", ""),
            )
    }

    override val reason: String =
        "The password must contain alphabet characters, numeric characters and at least one special character."

    override fun validate(rawPassword: String): Boolean =
        PASSWORD_COMPLEXITY_REG_EXP.matches(rawPassword)
}
