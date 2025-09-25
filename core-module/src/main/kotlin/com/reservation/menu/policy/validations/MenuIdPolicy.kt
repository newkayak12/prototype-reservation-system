package com.reservation.menu.policy.validations

interface MenuIdPolicy : MenuUnifiedValidationPolicy<String> {
    override fun validate(id: String): Boolean
}
