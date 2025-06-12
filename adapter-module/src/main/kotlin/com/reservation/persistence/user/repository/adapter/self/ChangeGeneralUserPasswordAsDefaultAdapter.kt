package com.reservation.persistence.user.repository.adapter.self

import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.user.self.port.output.UpdateGeneralUserTemporaryPassword
import com.reservation.user.self.port.output.UpdateGeneralUserTemporaryPassword.UpdateGeneralUserPasswordInquiry
import org.springframework.stereotype.Component

@Component
class ChangeGeneralUserPasswordAsDefaultAdapter(
    val jpaRepository: UserJpaRepository,
) : UpdateGeneralUserTemporaryPassword {
    override fun updateGeneralUserPassword(inquiry: UpdateGeneralUserPasswordInquiry): Boolean {
        val userEntity = jpaRepository.findById(inquiry.id)
        var result = false

        userEntity.ifPresent {
            it.changePassword(
                inquiry.newEncodedPassword,
                inquiry.oldEncodedPassword,
                inquiry.changedDateTime,
                inquiry.isNeedToChangePassword,
            )

            result = true
        }

        return result
    }
}
