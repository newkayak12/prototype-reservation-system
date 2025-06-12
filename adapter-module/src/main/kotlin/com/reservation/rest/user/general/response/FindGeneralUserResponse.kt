package com.reservation.rest.user.general.response

import com.reservation.enumeration.UserStatus
import com.reservation.user.self.port.input.FindGeneralUserQuery.FindGeneralUserQueryResult

data class FindGeneralUserResponse(
    val id: String,
    val loginId: String,
    val email: String,
    val nickname: String,
    val mobile: String,
    val userStatus: UserStatus,
) {
    companion object {
        fun from(result: FindGeneralUserQueryResult): FindGeneralUserResponse =
            FindGeneralUserResponse(
                result.id,
                result.loginId,
                result.email,
                result.nickname,
                result.mobile,
                result.userStatus,
            )
    }
}
