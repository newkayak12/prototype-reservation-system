package com.reservation.user.service

import com.reservation.user.common.exceptions.InvalidateUserElementException
import com.reservation.user.policy.availables.UserAttributeChangeable
import com.reservation.user.policy.validations.NicknameLengthValidationPolicy

class ChangeUserNicknameService {
    private val nicknameValidationPolicy =
        listOf(
            NicknameLengthValidationPolicy(),
        )

    private fun List<NicknameLengthValidationPolicy>.validatePolicies(target: String) {
        firstOrNull { !it.validate(target) }
            ?.let { throw InvalidateUserElementException(it.reason) }
    }

    private fun validateNickname(nickname: String) {
        nicknameValidationPolicy.validatePolicies(nickname)
    }

    fun <T : UserAttributeChangeable> changePersonalAttributes(
        target: T,
        nickname: String,
    ): T {
        validateNickname(nickname)

        target.changeUserNickname(nickname)

        return target
    }
}
