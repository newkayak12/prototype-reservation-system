package com.reservation.user.history.access.port.input.command.request

import com.reservation.enumeration.AccessStatus
import com.reservation.user.history.access.port.output.CreateUserAccessHistories.CreateUserHistoryInquiry
import com.reservation.user.shared.vo.LoginId
import java.time.LocalDateTime

data class CreateUserHistoryCommand(
    val authenticateId: String,
    val loginId: LoginId,
    val accessStatus: AccessStatus,
    val accessDateTime: LocalDateTime,
) {
    fun toDto(): CreateUserHistoryInquiry =
        CreateUserHistoryInquiry(
            authenticateId = authenticateId,
            loginId = loginId,
            accessStatus = accessStatus,
            accessDateTime = accessDateTime,
        )
}
