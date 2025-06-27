package com.reservation.user.self.port.output

import com.reservation.enumeration.Role
import com.reservation.enumeration.Role.USER
import com.reservation.enumeration.UserStatus
import com.reservation.enumeration.UserStatus.ACTIVATED
import com.reservation.user.self.port.input.FindGeneralUserQuery.FindGeneralUserQueryResult

@FunctionalInterface
interface FindGeneralUser {
    fun query(inquiry: FindGeneralUserInquiry): FindGeneralUserResult?

    data class FindGeneralUserInquiry(
        val id: String,
        val userStatus: UserStatus = ACTIVATED,
        val userRole: Role = USER,
    )

    data class FindGeneralUserResult(
        val id: String,
        val loginId: String,
        val email: String,
        val nickname: String,
        val mobile: String,
        val userStatus: UserStatus,
    ) {
        fun toQuery(): FindGeneralUserQueryResult =
            FindGeneralUserQueryResult(
                id,
                loginId,
                email,
                nickname,
                mobile,
                userStatus,
            )
    }
}
