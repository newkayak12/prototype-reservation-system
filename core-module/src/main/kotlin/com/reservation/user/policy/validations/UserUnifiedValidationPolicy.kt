package com.reservation.user.policy.validations

interface UserUnifiedValidationPolicy {
    val reason: String

    fun validate(target: String): Boolean
}
