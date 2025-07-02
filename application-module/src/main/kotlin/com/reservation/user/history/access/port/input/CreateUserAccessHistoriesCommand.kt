package com.reservation.user.history.access.port.input

import com.reservation.enumeration.AccessStatus
import com.reservation.user.history.access.port.output.CreateUserAccessHistories.CreateUserHistoryInquiry
import com.reservation.user.shared.LoginId
import java.time.LocalDateTime

fun interface CreateUserAccessHistoriesCommand {
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
