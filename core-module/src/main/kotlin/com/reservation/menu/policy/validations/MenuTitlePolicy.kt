package com.reservation.menu.policy.validations

interface MenuTitlePolicy : MenuUnifiedValidationPolicy<String> {
    override fun validate(title: String): Boolean
}
