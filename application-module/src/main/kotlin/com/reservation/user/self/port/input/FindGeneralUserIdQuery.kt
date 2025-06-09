package com.reservation.user.self.port.input

import com.reservation.user.self.port.output.FindGeneralUserId.FindGeneralUserIdInquiry

interface FindGeneralUserIdQuery {
    fun execute(query: FindGeneralUserIdQueryDto): List<FindGeneralUserIdQueryResult>

    data class FindGeneralUserIdQueryDto(
        val email: String,
    ) {
        public fun toInquiry(): FindGeneralUserIdInquiry = FindGeneralUserIdInquiry(email)
    }

    data class FindGeneralUserIdQueryResult(
        val userId: String,
    )
}
