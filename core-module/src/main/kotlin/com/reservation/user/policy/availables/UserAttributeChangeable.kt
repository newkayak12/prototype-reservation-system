package com.reservation.user.policy.availables

import com.reservation.shared.user.UserAttribute

interface UserAttributeChangeable {
    fun userAttributes(): UserAttribute

    fun changeUserNickname(userAttributes: UserAttribute)

    fun changeUserNickname(nickname: String) {
        changeUserNickname(userAttributes().updateUserNickname(nickname))
    }
}
