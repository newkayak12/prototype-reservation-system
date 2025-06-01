package com.reservation.user.policy.validations

interface EmailValidationPolicy : UserUnifiedValidationPolicy {
    override fun validate(email: String): Boolean
}
