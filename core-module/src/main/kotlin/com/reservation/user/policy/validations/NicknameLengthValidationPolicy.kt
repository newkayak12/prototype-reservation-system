package com.reservation.user.policy.validations

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
