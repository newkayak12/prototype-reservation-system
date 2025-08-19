package com.reservation.menu.policy.validations

import java.math.BigDecimal

class MenuPriceRangeValidationPolicy(
    override val reason: String =
        """
        Menu price has to between ${ZERO.longValueExact()} and ${MAX.longValueExact()}.
        """.trimIndent(),
) : MenuPricePolicy {
    companion object {
        private val ZERO = BigDecimal.ZERO
        private val MAX = BigDecimal.valueOf(999_999_999)
    }

    override fun validate(price: BigDecimal): Boolean = ZERO < price && price < MAX
}
