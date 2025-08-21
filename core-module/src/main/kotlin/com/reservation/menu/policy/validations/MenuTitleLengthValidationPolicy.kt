package com.reservation.menu.policy.validations

class MenuTitleLengthValidationPolicy(
    override val reason: String =
        "Menu Title has to " +
            "between ${MenuDescriptionLengthValidationPolicy.MIN_LENGTH} " +
            "and ${MenuDescriptionLengthValidationPolicy.MAX_LENGTH}.",
) : MenuTitlePolicy {
    companion object {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 30
    }

    override fun validate(title: String): Boolean = title.length in (MIN_LENGTH..MAX_LENGTH)
}
