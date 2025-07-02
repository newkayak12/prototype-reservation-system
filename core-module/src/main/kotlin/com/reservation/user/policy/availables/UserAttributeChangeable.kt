package com.reservation.user.policy.availables

import com.reservation.user.shared.UserAttribute

interface UserAttributeChangeable {
    fun userAttributes(): UserAttribute

    fun changeUserNickname(userAttributes: UserAttribute)

    fun changeUserNickname(nickname: String) {
        changeUserNickname(userAttributes().updateUserNickname(nickname))
    }
}
