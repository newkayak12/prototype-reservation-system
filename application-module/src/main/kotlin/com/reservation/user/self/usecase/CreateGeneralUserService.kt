package com.reservation.user.self.usecase

import com.reservation.common.exceptions.AlreadyPersistedException
import com.reservation.config.annotations.UseCase
import com.reservation.enumeration.Role.USER
import com.reservation.user.policy.formats.CreateGeneralUserForm
import com.reservation.user.self.port.input.CreateGeneralUserUseCase
import com.reservation.user.self.port.input.command.request.CreateGeneralUserCommand
import com.reservation.user.self.port.output.CheckGeneralUserLoginIdDuplicated
import com.reservation.user.self.port.output.CheckGeneralUserLoginIdDuplicated.CheckGeneralUserDuplicatedInquiry
import com.reservation.user.self.port.output.CreateGeneralUser
import com.reservation.user.self.port.output.CreateGeneralUser.CreateGeneralUserInquiry
import com.reservation.user.self.service.CreateGeneralUserDomainService
import org.springframework.transaction.annotation.Transactional

@UseCase
class CreateGeneralUserService(
    private val createGeneralUserDomainService: CreateGeneralUserDomainService,
    private val createGeneralUser: CreateGeneralUser,
    private val checkGeneralUserDuplicated: CheckGeneralUserLoginIdDuplicated,
) : CreateGeneralUserUseCase {
    @Transactional
    override fun execute(command: CreateGeneralUserCommand): Boolean {
        val user =
            createGeneralUserDomainService.createGeneralUser(
                CreateGeneralUserForm(
                    loginId = command.loginId,
                    password = command.password,
                    email = command.email,
                    mobile = command.mobile,
                    nickname = command.nickname,
                ),
            )

        if (
            checkGeneralUserDuplicated.query(
                CheckGeneralUserDuplicatedInquiry(command.loginId, USER),
            )
        ) {
            throw AlreadyPersistedException()
        }

        return createGeneralUser.command(
            CreateGeneralUserInquiry(
                loginId = user.userLoginId,
                password = user.userEncodedPassword,
                email = user.userEmail,
                mobile = user.userMobile,
                nickname = user.userNickname,
                role = user.userRole,
            ),
        )
    }
}
