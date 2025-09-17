package com.reservation.menu.policy.validations

import org.springframework.util.StringUtils

class MenIdIsNotEmptyValidationPolicy(
    override val reason: String = "Menu MUST NOT be empty",
) : MenuIdPolicy {
    override fun validate(id: String): Boolean = StringUtils.hasText(id)
}
