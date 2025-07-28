package com.reservation.user.service

import com.reservation.user.common.exceptions.InvalidateUserElementException
import com.reservation.user.policy.availables.UserAttributeChangeable
import com.reservation.user.policy.validations.NicknameLengthValidationPolicy

class ChangeUserNicknameDomainService {
    private val nicknameValidationPolicy =
        listOf(
            NicknameLengthValidationPolicy(),
        )

    private fun List<NicknameLengthValidationPolicy>.validatePolicies(target: String) {
        firstOrNull { !it.validate(target) }
            ?.let { throw InvalidateUserElementException(it.reason) }
    }

    /**
     * 닉네임 조건을 검증합니다.
     * [NicknameValidationPolicy]를 기반으로 작성됩니다.
     * 1. [NicknameLengthValidationPolicy]: 닉네임 길이에 대해서 검증합니다.
     */
    private fun validateNickname(nickname: String) {
        nicknameValidationPolicy.validatePolicies(nickname)
    }

    /**
     * @param target 대상
     * @param nickname 변경할 닉네임
     *
     * 닉네임을 변경합니다.
     */
    fun <T : UserAttributeChangeable> changePersonalAttributes(
        target: T,
        nickname: String,
    ): T {
        validateNickname(nickname)

        target.changeUserNickname(nickname)

        return target
    }
}
