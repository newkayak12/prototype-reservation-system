package com.reservation.user.history.change.port.output

import com.reservation.enumeration.Role

interface CreateGeneralUserChangeHistory {
    fun save(inquiry: CreateGeneralUserChangeHistoryInquiry)

    data class CreateGeneralUserChangeHistoryInquiry(
        val uuid: String,
        val userId: String,
        val email: String,
        val nickname: String,
        val mobile: String,
        val role: Role,
    )
}
