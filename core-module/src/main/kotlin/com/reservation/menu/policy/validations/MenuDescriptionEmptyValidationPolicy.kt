package com.reservation.menu.policy.validations

class MenuDescriptionEmptyValidationPolicy(
    override val reason: String = "Menu Description must not empty.",
) : MenuDescriptionPolicy {
    override fun validate(description: String): Boolean = description.isNotEmpty()
}
