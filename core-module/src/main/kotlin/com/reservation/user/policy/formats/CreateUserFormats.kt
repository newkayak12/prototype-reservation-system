package com.reservation.user.policy.formats

interface CreateUserFormats {
    fun loginId(): String

    fun password(): String

    fun email(): String

    fun mobile(): String

    fun nickname(): String
}
