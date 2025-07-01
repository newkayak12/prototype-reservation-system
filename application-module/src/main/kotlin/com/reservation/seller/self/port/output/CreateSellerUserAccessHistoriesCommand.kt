package com.reservation.seller.self.port.output

import com.reservation.enumeration.AccessStatus
import com.reservation.shared.user.LoginId
import com.reservation.user.history.access.port.output.CreateUserAccessHistories.CreateUserHistoryInquiry
import java.time.LocalDateTime

@FunctionalInterface
interface CreateSellerUserAccessHistoriesCommand {
    fun execute(histories: List<CreateSellerUserAccessHistoriesCommandDto>)

    data class CreateSellerUserAccessHistoriesCommandDto(
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
