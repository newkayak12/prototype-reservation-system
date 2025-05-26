package com.reservation.persistence.user.repository.adapter.self

import com.reservation.persistence.user.entity.UserEntity
import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.user.self.port.output.UpdateAuthenticateResult
import com.reservation.user.self.port.output.UpdateAuthenticateResult.UpdateAuthenticateResultDto
import org.springframework.stereotype.Component

@Component
class UpdateUserAdapter(
    private val userJpaRepository: UserJpaRepository,
) : UpdateAuthenticateResult {
    override fun save(authenticateResult: UpdateAuthenticateResultDto) {
        val userEntity: UserEntity =
            userJpaRepository.findById(authenticateResult.id)
                .orElseThrow {
                    throw NoSuchElementException()
                }

        userEntity.updateAuthenticateResult(
            authenticateResult.failCount,
            authenticateResult.lockedDateTime,
            authenticateResult.userStatus,
        )
    }
}
