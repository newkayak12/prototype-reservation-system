package com.reservation.user.policy.validations

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
