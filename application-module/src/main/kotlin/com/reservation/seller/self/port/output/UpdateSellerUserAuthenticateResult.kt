package com.reservation.seller.self.port.output

import com.reservation.enumeration.UserStatus
import java.time.LocalDateTime

@FunctionalInterface
interface UpdateSellerUserAuthenticateResult {
    fun command(authenticateResult: UpdateSellerUserAuthenticateResultDto)

    data class UpdateSellerUserAuthenticateResultDto(
        val id: String,
        val failCount: Int,
        var lockedDateTime: LocalDateTime?,
        val userStatus: UserStatus,
    )
}
