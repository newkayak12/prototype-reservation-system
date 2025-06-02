package com.reservation.user.self.service

import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.user.common.exceptions.InvalidateUserElementException
import com.reservation.user.policy.formats.CreateUserFormats
import com.reservation.user.policy.validations.EmailFormatValidationPolicy
import com.reservation.user.policy.validations.LoginIdAlphaNumericValidationPolicy
import com.reservation.user.policy.validations.LoginIdLengthValidationPolicy
import com.reservation.user.policy.validations.MobilePhoneFormatValidationPolicy
import com.reservation.user.policy.validations.NicknameLengthValidationPolicy
import com.reservation.user.policy.validations.PasswordComplexityValidationPolicy
import com.reservation.user.policy.validations.PasswordLengthValidationPolicy
import com.reservation.user.policy.validations.UserUnifiedValidationPolicy
import com.reservation.user.self.User
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility

class CreateGeneralUserService {
    private val loginIdValidationPolicy =
        listOf(
            LoginIdLengthValidationPolicy(),
            LoginIdAlphaNumericValidationPolicy(),
        )
    private val passwordValidationPolicy =
        listOf(
            PasswordLengthValidationPolicy(),
            PasswordComplexityValidationPolicy(),
        )
    private val emailValidationPolicy = listOf(EmailFormatValidationPolicy())
    private val mobilePhoneValidationPolicy = listOf(MobilePhoneFormatValidationPolicy())
    private val nicknameValidationPolicy = listOf(NicknameLengthValidationPolicy())

    private fun <T : UserUnifiedValidationPolicy> List<T>.validatePolicies(target: String) {
        firstOrNull { !it.validate(target) }
            ?.let { throw InvalidateUserElementException(it.reason) }
    }

    private fun validateLoginId(loginId: String) = loginIdValidationPolicy.validatePolicies(loginId)

    private fun validatePassword(password: String) =
        passwordValidationPolicy.validatePolicies(password)

    private fun validateEmail(email: String) = emailValidationPolicy.validatePolicies(email)

    private fun validateMobile(mobile: String) =
        mobilePhoneValidationPolicy.validatePolicies(mobile)

    private fun validateNickname(nickname: String) =
        nicknameValidationPolicy.validatePolicies(nickname)

    fun <T : CreateUserFormats> createGeneralUser(form: T): User {
        validateLoginId(form.loginId())
        validatePassword(form.password())
        validateEmail(form.email())
        validateMobile(form.mobile())
        validateNickname(form.nickname())

        return User(
            loginId = LoginId(form.loginId()),
            password =
                Password(
                    PasswordEncoderUtility.encode(form.password()),
                    null,
                    null,
                ),
            personalAttributes =
                PersonalAttributes(
                    email = form.email(),
                    mobile = form.mobile(),
                ),
            nickname = form.nickname(),
        )
    }
}
