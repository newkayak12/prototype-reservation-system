package com.reservation.user.general

import com.reservation.enumeration.Role.USER
import com.reservation.fixture.CommonlyUsedArbitraries
import com.reservation.user.policy.formats.CreateGeneralUserForm
import com.reservation.user.self.service.CreateGeneralUserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class CreateGeneralUserServiceTest : BehaviorSpec(
    {
        val createGeneralUserDomainService = CreateGeneralUserDomainService()

        Given("사용자 생성을 위해서 일반 사용자 Form을 작성한다.") {

            val createUserForm =
                CreateGeneralUserForm(
                    loginId = CommonlyUsedArbitraries.loginIdArbitrary.sample(),
                    password = CommonlyUsedArbitraries.passwordArbitrary.sample(),
                    email = CommonlyUsedArbitraries.emailArbitrary.sample(),
                    mobile = CommonlyUsedArbitraries.phoneNumberArbitrary.sample(),
                    nickname = CommonlyUsedArbitraries.nicknameArbitrary.sample(),
                )

            When("Form을 바탕으로 회원을 만들어달라고 요청한다.") {
                val actual = createGeneralUserDomainService.createGeneralUser(createUserForm)

                Then("처음 만들어진 사용자이므로 id는 null이다.") {
                    actual.identifier shouldBe null
                }

                Then("사용자의 Role은 일반 사용자이므로 USER이다.") {
                    actual.userRole shouldBe USER
                }

                Then("그외 입력한 필드는 empty가 아니다.") {
                    actual.userEmail.isNotEmpty() shouldBe true
                    actual.userMobile.isNotEmpty() shouldBe true
                    actual.userNickname.isNotEmpty() shouldBe true
                }
            }
        }
    },
)
