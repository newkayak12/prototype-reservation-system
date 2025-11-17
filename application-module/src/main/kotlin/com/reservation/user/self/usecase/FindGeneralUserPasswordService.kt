package com.reservation.user.self.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.user.self.User
import com.reservation.user.self.exceptions.UserFieldMustNotBeNullException
import com.reservation.user.self.port.input.FindGeneralUserPasswordUseCase
import com.reservation.user.self.port.input.query.request.FindGeneralUserPasswordCommand
import com.reservation.user.self.port.output.LoadGeneralUserByLoginIdAndEmail
import com.reservation.user.self.port.output.SendFindGeneralUserPasswordAsEmail
import com.reservation.user.self.port.output.SendFindGeneralUserPasswordAsEmail.FindGeneralUserPasswordEmailForm
import com.reservation.user.self.port.output.UpdateGeneralUserTemporaryPassword
import com.reservation.user.self.port.output.UpdateGeneralUserTemporaryPassword.UpdateGeneralUserPasswordInquiry
import com.reservation.user.service.ChangeGeneralUserPasswordDomainService
import com.reservation.utilities.generator.password.PasswordGenerator
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindGeneralUserPasswordService(
    private val changeGeneralUserPasswordDomainService: ChangeGeneralUserPasswordDomainService,
    private val loadGeneralUserByLoginIdAndEmail: LoadGeneralUserByLoginIdAndEmail,
    private val updateGeneralUserTemporaryPassword: UpdateGeneralUserTemporaryPassword,
    private val sendFindGeneralUserPasswordAsEmail: SendFindGeneralUserPasswordAsEmail,
) : FindGeneralUserPasswordUseCase {
    private fun sendChangePasswordResultEmail(
        email: String,
        password: String,
        isUpdateSuccess: Boolean,
    ) {
        if (!isUpdateSuccess) return
        sendFindGeneralUserPasswordAsEmail.execute(
            FindGeneralUserPasswordEmailForm(email, password),
        )
    }

    private fun checkRequiredElement(user: User) {
        if (
            user.identifier == null ||
            user.userOldEncodedPassword == null ||
            user.userPasswordChangedDatetime == null
        ) {
            throw UserFieldMustNotBeNullException()
        }
    }

    private fun updatePassword(user: User): Boolean {
        checkRequiredElement(user)
        return updateGeneralUserTemporaryPassword.command(
            UpdateGeneralUserPasswordInquiry(
                user.identifier!!,
                user.userEncodedPassword,
                user.userOldEncodedPassword!!,
                user.userPasswordChangedDatetime!!,
            ),
        )
    }

    @Transactional
    override fun execute(command: FindGeneralUserPasswordCommand): Boolean {
        val rawPassword = PasswordGenerator.createDefaultPassword()
        val user =
            loadGeneralUserByLoginIdAndEmail.load(command.toInquiry())
                ?.let {
                    changeGeneralUserPasswordDomainService.changePassword(
                        it.toDomain(),
                        rawPassword,
                        true,
                    )
                }
                ?: run { throw NoSuchPersistedElementException() }

        val result = updatePassword(user)
        sendChangePasswordResultEmail(command.email, rawPassword, result)

        return result
    }
}
