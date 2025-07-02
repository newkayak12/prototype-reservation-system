package com.reservation.user.self.port.output

import com.reservation.enumeration.Role

fun interface CreateGeneralUser {
    fun command(inquiry: CreateGeneralUserInquiry): Boolean

    data class CreateGeneralUserInquiry(
        val loginId: String,
        val password: String,
        val email: String,
        val mobile: String,
        val nickname: String,
        val role: Role,
    )
}
