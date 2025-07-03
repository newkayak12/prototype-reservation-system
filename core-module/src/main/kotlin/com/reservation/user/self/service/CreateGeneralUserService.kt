package com.reservation.user.self.service

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
import com.reservation.user.shared.vo.LoginId
import com.reservation.user.shared.vo.Password
import com.reservation.user.shared.vo.PersonalAttributes
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility

/**
 * 일반 회원 가입에 대한 Service입니다.
 * 아이디, 이메일, 휴대폰 번호, 비밀번호, 닉네임에 대한 검증 후
 * 회원 가입을 진행합니다.
 */
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

    /**
     * 주어진 정책으로 입력 값을 평가 합니다.
     * 사용하는 정책은 [com.reservation.user.policy.validations.LoginIdValidationPolicy]을 기반으로 작성됩니다.
     * 1. [LoginIdAlphaNumericValidationPolicy]: 사용자 아이디 구성
     * 2. [LoginIdLengthValidationPolicy]: 사용자 아이디 길이
     */
    private fun validateLoginId(loginId: String) = loginIdValidationPolicy.validatePolicies(loginId)

    /**
     * 주어진 정책으로 입력 값을 평가합니다.
     * 사용하는 정책은 [com.reservation.user.policy.validations.PasswordValidationPolicy]을 기반으로 작성됩니다.
     *
     * 1. [PasswordComplexityValidationPolicy] : 비밀번호 복잡도 정책
     * 2. [PasswordLengthValidationPolicy] : 비밀번호 길이 정책
     */
    private fun validatePassword(password: String) =
        passwordValidationPolicy.validatePolicies(password)

    /**
     * 주어진 정책으로 입력 값을 평가합니다.
     * 사용하는 정책은 [com.reservation.user.policy.validations.EmailValidationPolicy]을 기반으로 작성됩니다.
     *
     * 1. [EmailFormatValidationPolicy] : 이메일 형식에 대한 정책
     */
    private fun validateEmail(email: String) = emailValidationPolicy.validatePolicies(email)

    /**
     * 주어진 정책으로 입력 값을 평가합니다.
     * 사용하는 정책은 [com.reservation.user.policy.validations.MobilePhoneValidationPolicy]을 기반으로 작성됩니다.
     *
     * 1. [MobilePhoneFormatValidationPolicy] : 전화번호 형식에 대한 정책
     */
    private fun validateMobile(mobile: String) =
        mobilePhoneValidationPolicy.validatePolicies(mobile)

    /**
     * 주어진 정책으로 입력 값을 평가합니다.
     * 사용하는 정책은 [com.reservation.user.policy.validations.NicknameValidationPolicy]을 기반으로 작성됩니다.
     *
     * 1. [NicknameLengthValidationPolicy] : 닉네임 길에 대한 정책
     */
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
