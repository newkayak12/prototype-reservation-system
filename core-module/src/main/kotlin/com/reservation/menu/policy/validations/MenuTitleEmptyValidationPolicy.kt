package com.reservation.menu.policy.validations

class MenuTitleEmptyValidationPolicy(
    override val reason: String = "Menu Title must not empty.",
) : MenuTitlePolicy {
    override fun validate(title: String): Boolean = title.isNotEmpty()
}
