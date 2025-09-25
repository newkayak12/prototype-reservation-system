package com.reservation.menu.policy.validations

import org.springframework.util.StringUtils

class MenuIdIsNotEmptyValidationPolicy(
    override val reason: String = "Menu id must not be empty",
) : MenuIdPolicy {
    override fun validate(id: String): Boolean = StringUtils.hasText(id)
}
