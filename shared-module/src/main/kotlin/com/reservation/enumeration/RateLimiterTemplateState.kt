package com.reservation.enumeration

enum class RateLimiterTemplateState {
    ACTIVATED,
    DEACTIVATED,
    ;

    fun isActivated() = this == ACTIVATED
}
