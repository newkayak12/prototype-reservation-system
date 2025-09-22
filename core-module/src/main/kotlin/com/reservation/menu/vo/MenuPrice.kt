package com.reservation.menu.vo

import java.math.BigDecimal
import java.math.RoundingMode.UP

data class MenuPrice(
    val amount: BigDecimal = BigDecimal.ZERO,
) {
    init {
        amount.setScale(0, UP)
    }
}
