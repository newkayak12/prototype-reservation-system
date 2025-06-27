package com.reservation.user.policy.validations

/**
 * 닉네임 길이에 대한 정책을 표현합니다. 5 ~ 12 글자입니다.
 */
class NicknameLengthValidationPolicy : NicknameValidationPolicy {
    companion object {
        const val NICKNAME_MIN_LENGTH = 5
        const val NICKNAME_MAX_LENGTH = 12
    }

    override val reason: String =
        "The nickname length must be between $NICKNAME_MIN_LENGTH to $NICKNAME_MAX_LENGTH."

    override fun validate(nickname: String): Boolean =
        nickname.length in NICKNAME_MIN_LENGTH..NICKNAME_MAX_LENGTH
}
