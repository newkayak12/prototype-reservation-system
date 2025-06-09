package com.reservation.user.self.port.input

import com.reservation.user.self.port.output.FindGeneralUserIds.FindGeneralUserIdInquiry

interface FindGeneralUserIdsQuery {
    fun execute(query: FindGeneralUserIdQueryDto): List<FindGeneralUserIdQueryResult>

    data class FindGeneralUserIdQueryDto(
        val email: String,
    ) {
        fun toInquiry(): FindGeneralUserIdInquiry = FindGeneralUserIdInquiry(email)
    }

    data class FindGeneralUserIdQueryResult(
        val userId: String,
    )
}
