package com.reservation.user.policy.validations

class PasswordLengthValidationPolicy : PasswordValidationPolicy {
    companion object {
        const val MINIMUM_PASSWORD_LENGTH = 8
        const val MAXIMUM_PASSWORD_LENGTH = 18
        const val MAXIMUM_BYTE_ARRAY_LENGTH = 72
    }

    override val reason: String =
        "The password length must be between $MINIMUM_PASSWORD_LENGTH to $MAXIMUM_PASSWORD_LENGTH."

    override fun validate(rawPassword: String): Boolean =
        rawPassword.length in MINIMUM_PASSWORD_LENGTH..MAXIMUM_PASSWORD_LENGTH &&
            rawPassword.toByteArray(Charsets.UTF_8).size <= MAXIMUM_BYTE_ARRAY_LENGTH
}
