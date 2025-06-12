package com.reservation.user.self.port.input

import com.reservation.enumeration.UserStatus

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
