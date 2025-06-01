package com.reservation.user.policy.validations

class LoginIdAlphaNumericValidationPolicy : LoginIdValidationPolicy {
    companion object {
        val LOG_IN_ID_REG_EXP =
            Regex("^[a-zA-Z0-9]{4,20}$")
    }

    override val reason: String =
        "The login id must contain alphabet characters, numeric characters."

    override fun validate(loginId: String): Boolean = LOG_IN_ID_REG_EXP.matches(loginId)
}
