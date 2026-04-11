package com.reservation.persistence.user.repository.adapter.self

import com.reservation.persistence.user.entity.UserEntity
import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.user.self.port.output.CreateGeneralUser
import com.reservation.user.self.port.output.CreateGeneralUser.CreateGeneralUserInquiry
import org.springframework.stereotype.Component

@Component
class CreateGeneralUserAdapter(
    val jpaRepository: UserJpaRepository,
) : CreateGeneralUser {
    override fun command(inquiry: CreateGeneralUserInquiry): Boolean {
        val entity =
            jpaRepository.save(
                UserEntity(
                    loginId = inquiry.loginId,
                    password = inquiry.password,
                    email = inquiry.email,
                    nickname = inquiry.nickname,
                    mobile = inquiry.mobile,
                    role = inquiry.role,
                ),
            )

        return entity.identifier != null
    }
}
