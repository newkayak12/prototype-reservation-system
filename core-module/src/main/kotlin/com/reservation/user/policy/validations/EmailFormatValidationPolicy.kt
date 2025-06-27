package com.reservation.user.policy.validations

/**
 * 전형적인 이메일 형식에 대한 검사 정책을 표현합니다.
 */
class EmailFormatValidationPolicy : EmailValidationPolicy {
    companion object {
        val EMAIL_FORMAT_REG_EXP =
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
    }

    override val reason: String =
        "The email format is invalid. Please enter a valid email."

    override fun validate(email: String): Boolean = EMAIL_FORMAT_REG_EXP.matches(email)
}
