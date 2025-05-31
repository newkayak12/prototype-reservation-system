package com.reservation.user.general

import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.user.common.exceptions.InvalidateUserElementException
import com.reservation.user.policy.formats.CreateGeneralUserForm
import com.reservation.user.policy.validations.EmailFormatValidationPolicy
import com.reservation.user.policy.validations.LoginIdAlphaNumericValidationPolicy
import com.reservation.user.policy.validations.LoginIdLengthValidationPolicy
import com.reservation.user.policy.validations.MobilePhoneFormatValidationPolicy
import com.reservation.user.policy.validations.NicknameLengthValidationPolicy
import com.reservation.user.policy.validations.PasswordComplexityValidationPolicy
import com.reservation.user.policy.validations.PasswordLengthValidationPolicy
import com.reservation.user.self.service.CreateGeneralUserService
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.FunSpec
import net.jqwik.api.Arbitraries

class CreateGeneralUserServiceFailureTest : FunSpec(
    {
        val createGeneralUserService = CreateGeneralUserService()

        test("아이디 길이가 4 ~ 20을 만족하지 않는다.") {
            val message = LoginIdLengthValidationPolicy().reason
            val createUserForm =
                CreateGeneralUserForm(
                    loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample().substring(0, 3),
                    password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                    mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                )

            shouldThrowWithMessage<InvalidateUserElementException>(message) {
                createGeneralUserService.createGeneralUser(createUserForm)
            }
        }

        test("아이디가 영문, 숫자이외 다른 문자를 포함하여 아이디 정책에 만족하지 않는다.") {
            val message = LoginIdAlphaNumericValidationPolicy().reason
            val createUserForm =
                CreateGeneralUserForm(
                    loginId = "abcdefghijk@@",
                    password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                    mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                )

            shouldThrowWithMessage<InvalidateUserElementException>(message) {
                createGeneralUserService.createGeneralUser(createUserForm)
            }
        }

        test("이메일 형식에 맞지 않는다.") {
            val message = EmailFormatValidationPolicy().reason
            val createUserForm =
                CreateGeneralUserForm(
                    loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    email =
                        CommonlyUsedArbitraries.emailArbitrary.sample()
                            .replace("@", ""),
                    mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                )

            shouldThrowWithMessage<InvalidateUserElementException>(message) {
                createGeneralUserService.createGeneralUser(createUserForm)
            }
        }

        test("휴대폰 번호 형식에 맞지 않는다.") {
            val message = MobilePhoneFormatValidationPolicy().reason
            val createUserForm =
                CreateGeneralUserForm(
                    loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                    mobile =
                        CommonlyUsedArbitraries.phoneNumberArbitrary.sample()
                            .replace("010", "02"),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                )

            shouldThrowWithMessage<InvalidateUserElementException>(message) {
                createGeneralUserService.createGeneralUser(createUserForm)
            }
        }

        test("닉네임 형식에 맞지 않는다.") {
            val message = NicknameLengthValidationPolicy().reason
            val createUserForm =
                CreateGeneralUserForm(
                    loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                    mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample().substring(0, 4),
                )

            shouldThrowWithMessage<InvalidateUserElementException>(message) {
                createGeneralUserService.createGeneralUser(createUserForm)
            }
        }

        test("비밀번호 길이 8 ~ 18에 맞지 않는다.") {
            val message = PasswordLengthValidationPolicy().reason
            val createUserForm =
                CreateGeneralUserForm(
                    loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    password = CommonlyUsedArbitraries.passwordArbitrary.sample().substring(0, 7),
                    email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                    mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                )

            shouldThrowWithMessage<InvalidateUserElementException>(message) {
                createGeneralUserService.createGeneralUser(createUserForm)
            }
        }

        test("비밀번호 형식에 맞지 않는다.") {
            val message = PasswordComplexityValidationPolicy().reason
            val createUserForm =
                CreateGeneralUserForm(
                    loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    password =
                        Arbitraries.strings().ofMinLength(8).ofMaxLength(18).alpha()
                            .sample(),
                    email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                    mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                )

            shouldThrowWithMessage<InvalidateUserElementException>(message) {
                createGeneralUserService.createGeneralUser(createUserForm)
            }
        }
    },
)
