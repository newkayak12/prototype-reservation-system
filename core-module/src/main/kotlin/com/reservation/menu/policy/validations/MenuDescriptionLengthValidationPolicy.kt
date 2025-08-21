package com.reservation.menu.policy.validations

class MenuDescriptionLengthValidationPolicy(
    override val reason: String = "Menu Description has to between $MIN_LENGTH and $MAX_LENGTH.",
) : MenuDescriptionPolicy {
    companion object {
        const val MIN_LENGTH = 1
        const val MAX_LENGTH = 255
    }

    override fun validate(description: String): Boolean =
        description.length in (MIN_LENGTH..MAX_LENGTH)
}
