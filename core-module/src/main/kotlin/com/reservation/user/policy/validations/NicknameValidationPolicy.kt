package com.reservation.user.policy.validations

interface NicknameValidationPolicy : UserUnifiedValidationPolicy {
    override fun validate(nickname: String): Boolean
}
