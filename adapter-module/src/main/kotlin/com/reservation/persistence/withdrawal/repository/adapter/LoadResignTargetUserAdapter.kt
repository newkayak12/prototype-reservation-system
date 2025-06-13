package com.reservation.persistence.withdrawal.repository.adapter

import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.resign.port.output.LoadResignTargetUser
import com.reservation.resign.port.output.LoadResignTargetUser.LoadResignTargetResult
import org.springframework.stereotype.Component

@Component
class LoadResignTargetUserAdapter(
    val jpaRepository: UserJpaRepository,
) : LoadResignTargetUser {
    override fun load(id: String): LoadResignTargetResult? {
        return jpaRepository.findById(id)
            .map {
                LoadResignTargetResult(
                    it.id!!,
                    it.loginId,
                    it.password,
                    it.oldPassword,
                    it.passwordChangeDateTime,
                    it.email,
                    it.mobile,
                    it.nickname,
                )
            }
            .orElse(null)
    }
}
