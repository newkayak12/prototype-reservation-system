package com.reservation.user.self.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.user.self.exceptions.UserFieldMustNotBeNullException
import com.reservation.user.self.port.input.ChangeGeneralUserPasswordUseCase
import com.reservation.user.self.port.input.command.request.ChangeGeneralUserPasswordCommand
import com.reservation.user.self.port.output.ChangeGeneralUserPassword
import com.reservation.user.self.port.output.ChangeGeneralUserPassword.ChangeGeneralUserPasswordInquiry
import com.reservation.user.self.port.output.LoadGeneralUser
import com.reservation.user.service.ChangeGeneralUserPasswordDomainService
import org.springframework.transaction.annotation.Transactional

@UseCase
class ChangeGeneralUserPasswordService(
    private val changeGeneralUserPasswordDomainService: ChangeGeneralUserPasswordDomainService,
    private val changeGeneralUserPassword: ChangeGeneralUserPassword,
    private val loadGeneralUser: LoadGeneralUser,
) : ChangeGeneralUserPasswordUseCase {
    @Transactional
    override fun execute(command: ChangeGeneralUserPasswordCommand): Boolean {
        val user =
            loadGeneralUser.load(command.id)?.let {
                changeGeneralUserPasswordDomainService.changePassword(
                    it.toDomain(),
                    command.encodedPassword,
                )
            } ?: run { throw NoSuchPersistedElementException() }

        return changeGeneralUserPassword.command(
            ChangeGeneralUserPasswordInquiry(
                user.identifier ?: run { throw UserFieldMustNotBeNullException() },
                user.userEncodedPassword,
                user.userOldEncodedPassword ?: run { throw UserFieldMustNotBeNullException() },
                user.userPasswordChangedDatetime ?: run { throw UserFieldMustNotBeNullException() },
            ),
        )
    }
}
