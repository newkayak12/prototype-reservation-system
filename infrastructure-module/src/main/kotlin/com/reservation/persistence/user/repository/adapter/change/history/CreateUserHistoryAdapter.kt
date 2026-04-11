package com.reservation.persistence.user.repository.adapter.change.history

import com.reservation.persistence.user.entity.UserChangeHistoryEntity
import com.reservation.persistence.user.repository.jpa.UserChangeHistoryJpaRepository
import com.reservation.user.history.change.port.output.CreateGeneralUserChangeHistory
import com.reservation.user.history.change.port.output.CreateGeneralUserChangeHistory.CreateGeneralUserChangeHistoryInquiry
import org.springframework.stereotype.Component

@Component
class CreateUserHistoryAdapter(
    val userChangeHistoryJpaRepository: UserChangeHistoryJpaRepository,
) : CreateGeneralUserChangeHistory {
    override fun save(inquiry: CreateGeneralUserChangeHistoryInquiry) {
        val entity =
            UserChangeHistoryEntity(
                inquiry.uuid,
                inquiry.userId,
                inquiry.email,
                inquiry.nickname,
                inquiry.mobile,
                inquiry.role,
            )

        userChangeHistoryJpaRepository.save(entity)
    }
}
