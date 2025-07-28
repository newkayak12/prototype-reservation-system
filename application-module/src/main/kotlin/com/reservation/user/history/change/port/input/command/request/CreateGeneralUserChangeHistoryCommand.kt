package com.reservation.user.history.change.port.input.command.request

import com.reservation.enumeration.Role
import com.reservation.enumeration.Role.USER
import com.reservation.user.history.change.port.output.CreateGeneralUserChangeHistory.CreateGeneralUserChangeHistoryInquiry

data class CreateGeneralUserChangeHistoryCommand(
    val uuid: String,
    val userId: String,
    val email: String,
    val nickname: String,
    val mobile: String,
    val role: Role = USER,
) {
    fun toInquiry(): CreateGeneralUserChangeHistoryInquiry {
        return CreateGeneralUserChangeHistoryInquiry(
            uuid,
            userId,
            email,
            nickname,
            mobile,
            role,
        )
    }
}
