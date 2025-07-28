package com.reservation.user.self.port.input.query.response

import com.reservation.enumeration.UserStatus

data class FindGeneralUserQueryResult(
    val id: String,
    val loginId: String,
    val email: String,
    val nickname: String,
    val mobile: String,
    val userStatus: UserStatus,
)
