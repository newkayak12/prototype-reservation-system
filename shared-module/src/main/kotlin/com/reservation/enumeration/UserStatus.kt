package com.reservation.enumeration

enum class UserStatus {
    ACTIVATED,
    DEACTIVATED,
    ;

    fun isActivated(): Boolean = this == ACTIVATED

    fun isDeactivated(): Boolean = this == DEACTIVATED
}
