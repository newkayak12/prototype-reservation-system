package com.reservation.menu.policy.validations

class MenuIdFormatValidationPolicy(
    override val reason: String = "Invalid ID Format",
) : MenuIdPolicy {
    companion object {
        const val FORMAT =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        val UUID_REGEX =
            Regex(FORMAT)
    }

    override fun validate(id: String): Boolean = UUID_REGEX.matches(id)
}
