package com.reservation.user.resign.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.user.resign.port.input.ResignUserUseCase
import com.reservation.user.resign.port.output.LoadResignTargetUser
import com.reservation.user.resign.port.output.ResignTargetUser
import com.reservation.user.resign.port.output.ResignTargetUser.ResignInquiry
import com.reservation.user.resign.service.ResignUserDomainService
import org.springframework.transaction.annotation.Transactional

@UseCase
class ResignService(
    private val resignUserService: ResignUserDomainService,
    private val loadResignTargetUser: LoadResignTargetUser,
    private val resignTargetUser: ResignTargetUser,
) : ResignUserUseCase {
    @Transactional
    override fun execute(id: String): Boolean {
        val user =
            loadResignTargetUser.load(id)?.toDomain()
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
