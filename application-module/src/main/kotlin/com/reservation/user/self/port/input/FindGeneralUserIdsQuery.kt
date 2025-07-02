package com.reservation.user.self.port.input

import com.reservation.user.self.port.output.FindGeneralUserIds.FindGeneralUserIdInquiry

/**
 * 일반 사용자에 아이디 찾기를 요구합니다.
 * 조건에 맞는 아이디들을 찾고 이를 리턴합니다.
 */
fun interface FindGeneralUserIdsQuery {
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
