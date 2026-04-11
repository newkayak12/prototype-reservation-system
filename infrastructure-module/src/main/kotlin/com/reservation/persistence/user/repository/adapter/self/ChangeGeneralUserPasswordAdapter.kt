package com.reservation.persistence.user.repository.adapter.self

import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.user.self.port.output.ChangeGeneralUserPassword
import com.reservation.user.self.port.output.ChangeGeneralUserPassword.ChangeGeneralUserPasswordInquiry
import org.springframework.stereotype.Component

@Component
class ChangeGeneralUserPasswordAdapter(
    val jpaRepository: UserJpaRepository,
) : ChangeGeneralUserPassword {
    override fun command(inquiry: ChangeGeneralUserPasswordInquiry): Boolean {
        val userEntity = jpaRepository.findById(inquiry.id)
        var result = false

        userEntity.ifPresent {
            it.changePassword(
                inquiry.encodedPassword,
                inquiry.oldEncodedPassword,
                inquiry.changedDateTime,
            )

            result = true
        }

        return result
    }
}
