package com.reservation.persistence.user.repository.adapter.access.history

import com.reservation.persistence.user.entity.UserAccessHistoryEntity
import com.reservation.persistence.user.repository.jpa.UserAccessHistoryJpaRepository
import com.reservation.user.history.access.port.output.CreateUserAccessHistories
import com.reservation.user.history.access.port.output.CreateUserAccessHistories.CreateUserHistoryInquiry
import org.springframework.stereotype.Component

@Component
class CreateUserAccessAdapter(
    val userAccessHistoryJpaRepository: UserAccessHistoryJpaRepository,
) : CreateUserAccessHistories {
    override fun saveAll(histories: List<CreateUserHistoryInquiry>) {
        userAccessHistoryJpaRepository.saveAll(
            histories.map {
                UserAccessHistoryEntity(
                    it.authenticateId,
                    it.accessStatus,
                    it.accessDateTime,
                )
            },
        )
    }
}
