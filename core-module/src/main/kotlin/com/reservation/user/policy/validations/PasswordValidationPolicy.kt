package com.reservation.user.policy.validations

interface PasswordValidationPolicy : UserUnifiedValidationPolicy {
    override fun validate(rawPassword: String): Boolean
}
