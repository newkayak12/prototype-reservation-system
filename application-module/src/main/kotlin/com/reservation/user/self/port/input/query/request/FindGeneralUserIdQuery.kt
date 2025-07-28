package com.reservation.user.self.port.input.query.request

import com.reservation.user.self.port.output.FindGeneralUserIds.FindGeneralUserIdInquiry

data class FindGeneralUserIdQuery(
    val email: String,
) {
    fun toInquiry(): FindGeneralUserIdInquiry = FindGeneralUserIdInquiry(email)
}
