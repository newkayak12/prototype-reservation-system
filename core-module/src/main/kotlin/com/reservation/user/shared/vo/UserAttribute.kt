package com.reservation.user.shared.vo

import com.reservation.enumeration.Role

data class UserAttribute(
    val nickname: String,
    val role: Role,
) {
    fun updateUserNickname(newNickname: String): UserAttribute {
        return UserAttribute(newNickname, role)
    }
}
