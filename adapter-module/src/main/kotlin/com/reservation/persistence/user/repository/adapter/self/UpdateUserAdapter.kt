package com.reservation.persistence.user.repository.adapter.self

import com.reservation.authenticate.port.output.UpdateAuthenticateResult
import com.reservation.authenticate.port.output.UpdateAuthenticateResult.UpdateAuthenticateResultDto
import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.persistence.user.entity.UserEntity
import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import org.springframework.stereotype.Component

@Component
class UpdateUserAdapter(
    private val userJpaRepository: UserJpaRepository,
) : UpdateAuthenticateResult {
    override fun command(authenticateResult: UpdateAuthenticateResultDto) {
        val userEntity: UserEntity =
            userJpaRepository.findById(authenticateResult.id)
                .orElseThrow {
                    throw NoSuchPersistedElementException()
                }

        userEntity.updateAuthenticateResult(
            authenticateResult.failCount,
            authenticateResult.lockedDateTime,
            authenticateResult.userStatus,
        )
    }
}
