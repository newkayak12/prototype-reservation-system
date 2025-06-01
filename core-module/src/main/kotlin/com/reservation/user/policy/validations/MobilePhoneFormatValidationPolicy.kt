package com.reservation.user.policy.validations

class MobilePhoneFormatValidationPolicy : MobilePhoneValidationPolicy {
    companion object {
        val MOBILE_FORMAT_REG_EXP =
            Regex("^01([0|1|6|7|8|9])-?\\d{3,4}-?\\d{4}$")
    }

    override val reason: String =
        "The mobile phone number format is invalid. Please enter a valid number."

    override fun validate(mobile: String): Boolean = MOBILE_FORMAT_REG_EXP.matches(mobile)
}
