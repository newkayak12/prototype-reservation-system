package com.reservation.persistence.user.repository.adapter.self

import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.user.self.port.output.LoadGeneralUserByLoginIdAndEmail
import com.reservation.user.self.port.output.LoadGeneralUserByLoginIdAndEmail.LoadGeneralUserByLoginIdAndEmailInquiry
import com.reservation.user.self.port.output.LoadGeneralUserByLoginIdAndEmail.LoadGeneralUserByLoginIdAndEmailResult
import org.springframework.stereotype.Component

@Component
class LoadGeneralUserLoginIdAndEmailAdapter(
    val jpaRepository: UserJpaRepository,
) : LoadGeneralUserByLoginIdAndEmail {
    override fun load(
        inquiry: LoadGeneralUserByLoginIdAndEmailInquiry,
    ): LoadGeneralUserByLoginIdAndEmailResult? {
        return jpaRepository.findUserEntityByLoginIdEqualsAndEmailEquals(
            inquiry.loginId,
            inquiry.email,
        )
            .map {
                LoadGeneralUserByLoginIdAndEmailResult(
                    it.identifier!!,
                    it.loginId,
                    it.password,
                    it.oldPassword,
                    it.passwordChangeDateTime,
                    it.isNeedToChangePassword,
                    it.email,
                    it.mobile,
                    it.nickname,
                )
            }
            .orElse(null)
    }
}
