package com.reservation.persistence.withdrawal.repository.adapter

import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.persistence.withdrawal.entity.WithdrawalUserEntity
import com.reservation.persistence.withdrawal.repository.jpa.WithdrawalJpaRepository
import com.reservation.resign.port.output.ResignTargetUser
import com.reservation.resign.port.output.ResignTargetUser.ResignInquiry
import org.springframework.stereotype.Component

@Component
class ResignTargetUserAdapter(
    val jpaRepository: WithdrawalJpaRepository,
    val userJpaRepository: UserJpaRepository,
) : ResignTargetUser {
    override fun command(inquiry: ResignInquiry): Boolean {
        val withdrawal =
            WithdrawalUserEntity(
                inquiry.userLoginId,
                inquiry.encryptedEmail,
                inquiry.encryptedNickname,
                inquiry.encryptedMobile,
                inquiry.encryptedRole,
            )

        jpaRepository.save(withdrawal)
        userJpaRepository.deleteById(inquiry.id)

        return true
    }
}
