package com.reservation.menu.policy.validations

interface MenuRestaurantIdValidationPolicy : MenuUnifiedValidationPolicy<String> {
    override fun validate(target: String): Boolean
}
