package com.reservation.user.policy.validations

interface LoginIdValidationPolicy : UserUnifiedValidationPolicy {
    override fun validate(loginId: String): Boolean
}
