package com.reservation.user.self.port.output

import com.reservation.enumeration.UserStatus
import java.time.LocalDateTime

interface UpdateAuthenticateResult {
    fun save(authenticateResult: UpdateAuthenticateResultDto)

    data class UpdateAuthenticateResultDto(
        val id: String,
        val failCount: Int,
        var lockedDateTime: LocalDateTime?,
        val userStatus: UserStatus,
    )
}
