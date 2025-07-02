package com.reservation.resign.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.resign.port.input.ResignUserCommand
import com.reservation.resign.port.output.LoadResignTargetUser
import com.reservation.resign.port.output.ResignTargetUser
import com.reservation.resign.port.output.ResignTargetUser.ResignInquiry
import com.reservation.user.resign.service.ResignUserService
import org.springframework.transaction.annotation.Transactional

@UseCase
class ResignUseCase(
    private val resignUserService: ResignUserService,
    private val loadResignTargetUser: LoadResignTargetUser,
    private val resignTargetUser: ResignTargetUser,
) : ResignUserCommand {
    @Transactional
    override fun execute(id: String): Boolean {
        val user =
            loadResignTargetUser.load(id)
                ?.let { it.toDomain() }
                ?: throw NoSuchPersistedElementException()

        val resignUser = resignUserService.resign(user)
        val resignTarget =
            ResignInquiry(
                resignUser.id,
                resignUser.loginId,
                resignUser.encryptedAttributes,
                resignUser.withdrawalDateTime,
            )
        return resignTargetUser.command(resignTarget)
    }
}
