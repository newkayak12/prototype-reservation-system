package com.reservation.user.policy.validations

/**
 * 아이디 길이에 대한 정책입니다. 4이상 20자 이하로 작성해야만 합니다.
 */
class LoginIdLengthValidationPolicy : LoginIdValidationPolicy {
    companion object {
        const val LOG_IN_MIN_LENGTH = 4
        const val LOG_IN_MAX_LENGTH = 20
    }

    override val reason: String =
        "The login id length must be between $LOG_IN_MIN_LENGTH to $LOG_IN_MAX_LENGTH."

    override fun validate(loginId: String): Boolean =
        loginId.length in LOG_IN_MIN_LENGTH..LOG_IN_MAX_LENGTH
}
