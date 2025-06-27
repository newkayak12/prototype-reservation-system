package com.reservation.user.service

import com.reservation.user.common.exceptions.InvalidateUserElementException
import com.reservation.user.common.exceptions.UseSamePasswordAsBeforeException
import com.reservation.user.policy.availables.PasswordChangeable
import com.reservation.user.policy.validations.PasswordComplexityValidationPolicy
import com.reservation.user.policy.validations.PasswordLengthValidationPolicy
import com.reservation.user.policy.validations.UserUnifiedValidationPolicy
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility

class ChangeGeneralUserPasswordService {
    private val passwordPolicy =
        listOf(
            PasswordLengthValidationPolicy(),
            PasswordComplexityValidationPolicy(),
        )

    private fun <T : UserUnifiedValidationPolicy> List<T>.validatePolicies(target: String) {
        firstOrNull { !it.validate(target) }
            ?.let { throw InvalidateUserElementException(it.reason) }
    }

    /**
     * @param target 변경 대상
     * @param rawPassword 바꿀 비밀번호
     * @param isNeedToChangePassword 비밀번호 변경 후 비밀번호 변경 요구를 해야 하는가? -> 비밀번호 재발급의 경우
     */
    fun <T : PasswordChangeable> changePassword(
        target: T,
        rawPassword: String,
        isNeedToChangePassword: Boolean = false,
    ): T {
        validatePassword(rawPassword)

        val passwordSet = target.userPasswordSet
        val currentPassword = passwordSet.encodedPassword
        validateAlreadyUsedPassword(rawPassword, currentPassword)

        return target.apply {
            changePassword(
                PasswordEncoderUtility.encode(rawPassword),
                isNeedToChangePassword,
            )
        }
    }

    /**
     * 주어진 정책으로 입력 값을 평가합니다.
     * 사용하는 정책은 [com.reservation.user.policy.validations.PasswordValidationPolicy]을 기반으로 작성됩니다.
     *
     * 1. [PasswordComplexityValidationPolicy] : 비밀번호 복잡도 정책
     * 2. [PasswordLengthValidationPolicy] : 비밀번호 길이 정책
     */
    private fun validatePassword(rawPassword: String) {
        passwordPolicy.validatePolicies(rawPassword)
    }

    /**
     * 해당 비밀번호가 이미 사용된 비밀번호인지 검증합니다.
     */
    private fun validateAlreadyUsedPassword(
        newPassword: String,
        oldPassword: String,
    ) {
        if (PasswordEncoderUtility.matches(newPassword, oldPassword)) {
            throw UseSamePasswordAsBeforeException()
        }
    }
}
