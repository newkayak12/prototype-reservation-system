package com.reservation.menu.policy.validations

interface MenuUnifiedValidationPolicy<T> {
    val reason: String

    fun validate(target: T): Boolean
}
