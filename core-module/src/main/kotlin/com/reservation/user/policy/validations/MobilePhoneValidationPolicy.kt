package com.reservation.user.policy.validations

interface MobilePhoneValidationPolicy : UserUnifiedValidationPolicy {
    override fun validate(mobile: String): Boolean
}
