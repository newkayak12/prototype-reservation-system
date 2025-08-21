package com.reservation.menu.policy.validations

interface MenuDescriptionPolicy : MenuUnifiedValidationPolicy<String> {
    override fun validate(description: String): Boolean
}
