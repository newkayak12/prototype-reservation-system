package com.reservation.menu.policy.validations

import java.math.BigDecimal

interface MenuPricePolicy : MenuUnifiedValidationPolicy<BigDecimal> {
    override fun validate(price: BigDecimal): Boolean
}
