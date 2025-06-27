package com.reservation.user.self.port.input

import com.reservation.enumeration.UserStatus

/**
 * 일반 사용자의 정보 조회를 요구합니다.
 */
@FunctionalInterface
interface FindGeneralUserQuery {
    fun execute(id: String): FindGeneralUserQueryResult

    data class FindGeneralUserQueryResult(
        val id: String,
        val loginId: String,
        val email: String,
        val nickname: String,
        val mobile: String,
        val userStatus: UserStatus,
    )
}
