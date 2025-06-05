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

    fun <T : PasswordChangeable> changePassword(
        target: T,
        rawPassword: String,
    ): T {
        validatePassword(rawPassword)

        val passwordSet = target.userPasswordSet
        val currentPassword = passwordSet.encodedPassword
        validateAlreadyUsedPassword(rawPassword, currentPassword)

        return target.apply {
            changePassword(PasswordEncoderUtility.encode(rawPassword))
        }
    }

    private fun validatePassword(rawPassword: String) {
        passwordPolicy.validatePolicies(rawPassword)
    }

    private fun validateAlreadyUsedPassword(
        newPassword: String,
        oldPassword: String,
    ) {
        if (PasswordEncoderUtility.matches(newPassword, oldPassword)) {
            throw UseSamePasswordAsBeforeException()
        }
    }
}
