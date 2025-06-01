package com.reservation.user.policy.validations

class EmailFormatValidationPolicy : EmailValidationPolicy {
    companion object {
        val EMAIL_FORMAT_REG_EXP =
            Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$")
    }

    override val reason: String =
        "The email format is invalid. Please enter a valid email."

    override fun validate(email: String): Boolean = EMAIL_FORMAT_REG_EXP.matches(email)
}
