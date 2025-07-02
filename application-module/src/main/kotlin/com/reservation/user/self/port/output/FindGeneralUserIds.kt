package com.reservation.user.self.port.output

fun interface FindGeneralUserIds {
    fun query(inquiry: FindGeneralUserIdInquiry): List<FindGeneralUserIdResult>

    data class FindGeneralUserIdInquiry(
        val email: String,
    )

    data class FindGeneralUserIdResult(
        val userId: String,
    )
}
