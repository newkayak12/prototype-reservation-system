package com.reservation.user.self.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.exceptions.InvalidSituationException
import com.reservation.user.exceptions.NoSuchDatabaseElementException
import com.reservation.user.self.port.input.ChangeGeneralUserPasswordCommand
import com.reservation.user.self.port.input.ChangeGeneralUserPasswordCommand.ChangeGeneralUserPasswordCommandDto
import com.reservation.user.self.port.output.ChangeGeneralUserPassword
import com.reservation.user.self.port.output.ChangeGeneralUserPassword.ChangeGeneralUserPasswordInquiry
import com.reservation.user.self.port.output.LoadGeneralUser
import com.reservation.user.service.ChangeGeneralUserPasswordService
import org.springframework.transaction.annotation.Transactional

@UseCase
class ChangeGeneralUserPasswordUseCase(
    private val changeGeneralUserPasswordService: ChangeGeneralUserPasswordService,
    private val changeGeneralUserPassword: ChangeGeneralUserPassword,
    private val loadGeneralUser: LoadGeneralUser,
) : ChangeGeneralUserPasswordCommand {
    @Transactional
    override fun execute(command: ChangeGeneralUserPasswordCommandDto): Boolean {
        val user =
            loadGeneralUser.load(command.id)?.let {
                changeGeneralUserPasswordService.changePassword(
                    it.toDomain(),
                    command.encodedPassword,
                )
            } ?: run { throw NoSuchDatabaseElementException() }

        return changeGeneralUserPassword.command(
            ChangeGeneralUserPasswordInquiry(
                user.identifier ?: run { throw InvalidSituationException() },
                user.userEncodedPassword,
                user.userOldEncodedPassword ?: run { throw InvalidSituationException() },
                user.userPasswordChangedDatetime ?: run { throw InvalidSituationException() },
            ),
        )
    }
}
