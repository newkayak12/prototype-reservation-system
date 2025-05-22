package com.reservation.shared.user

import com.reservation.enumeration.Role

data class UserAttribute(
    val nickname: String,
    val role: Role,
)
