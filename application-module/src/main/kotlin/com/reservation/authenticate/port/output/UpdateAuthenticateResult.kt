package com.reservation.authenticate.port.output

import com.reservation.enumeration.UserStatus
import java.time.LocalDateTime

interface UpdateAuthenticateResult {
    fun command(authenticateResult: UpdateAuthenticateResultDto)

    data class UpdateAuthenticateResultDto(
        val id: String,
        val failCount: Int,
        var lockedDateTime: LocalDateTime?,
        val userStatus: UserStatus,
    )
}
