package com.reservation.persistence.user.repository.adapter.self

import com.reservation.persistence.user.repository.jpa.UserJpaRepository
import com.reservation.user.self.port.output.ChangeGeneralUserNickname
import com.reservation.user.self.port.output.ChangeGeneralUserNickname.ChangeGeneralUserNicknameDto
import org.springframework.stereotype.Component

@Component
class ChangeGeneralUserNicknameAdapter(
    val jpaRepository: UserJpaRepository,
) : ChangeGeneralUserNickname {
    override fun command(inquiry: ChangeGeneralUserNicknameDto): Boolean {
        val userEntity = jpaRepository.findById(inquiry.id)
        var result = false

        userEntity.ifPresent {
            it.changeNickname(inquiry.nickname)
            result = true
        }

        return result
    }
}
