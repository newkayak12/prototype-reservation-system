package com.reservation.persistence.user.repository.adapter.self

import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.user.self.port.output.LoadGeneralUser
import com.reservation.user.self.port.output.LoadGeneralUser.LoadGeneralUserResult
import org.springframework.stereotype.Component

@Component
class LoadGeneralUserAdapter(
    val jpaRepository: UserJpaRepository,
) : LoadGeneralUser {
    override fun load(id: String): LoadGeneralUserResult? {
        return jpaRepository.findById(id)
            .map {
                LoadGeneralUserResult(
                    it.identifier!!,
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
