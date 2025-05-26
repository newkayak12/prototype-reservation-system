package com.reservation.user.history.access.port.input

import com.reservation.enumeration.AccessStatus
import com.reservation.shared.user.LoginId
import com.reservation.user.history.access.port.output.CreateUserAccessHistories.CreateUserHistoryInquiry
import java.time.LocalDateTime

interface CreateUserAccessHistoriesCommand {
    fun execute(histories: List<CreateUserHistoryCommandDto>)

    data class CreateUserHistoryCommandDto(
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
}
