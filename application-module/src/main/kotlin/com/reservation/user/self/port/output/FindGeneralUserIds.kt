package com.reservation.user.self.port.output

@FunctionalInterface
interface FindGeneralUserIds {
    fun findGeneralUserId(inquiry: FindGeneralUserIdInquiry): List<FindGeneralUserIdResult>

    data class FindGeneralUserIdInquiry(
        val email: String,
    )

    data class FindGeneralUserIdResult(
        val userId: String,
    )
}
