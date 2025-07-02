package com.reservation.user.history.access.port.output

import com.reservation.enumeration.AccessStatus
import com.reservation.user.shared.LoginId
import java.time.LocalDateTime

@FunctionalInterface
interface CreateUserAccessHistories {
    fun saveAll(histories: List<CreateUserHistoryInquiry>)

    data class CreateUserHistoryInquiry(
        val authenticateId: String,
        val loginId: LoginId,
        val accessStatus: AccessStatus,
        val accessDateTime: LocalDateTime,
    )
}
