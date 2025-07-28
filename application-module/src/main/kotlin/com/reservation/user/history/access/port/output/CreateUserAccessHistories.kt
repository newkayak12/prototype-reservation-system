package com.reservation.user.history.access.port.output

import com.reservation.enumeration.AccessStatus
import com.reservation.user.shared.vo.LoginId
import java.time.LocalDateTime

interface CreateUserAccessHistories {
    fun saveAll(histories: List<CreateUserHistoryInquiry>)

    data class CreateUserHistoryInquiry(
        val authenticateId: String,
        val loginId: LoginId,
        val accessStatus: AccessStatus,
        val accessDateTime: LocalDateTime,
    )
}
