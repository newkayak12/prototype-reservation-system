package com.reservation.menu.policy.validations

class MenuRestaurantIdEmptyValidationPolicy(
    override val reason: String = "Menu-RestaurantId must not empty.",
) : MenuRestaurantIdValidationPolicy {
    override fun validate(target: String): Boolean = target.isNotEmpty()
}
