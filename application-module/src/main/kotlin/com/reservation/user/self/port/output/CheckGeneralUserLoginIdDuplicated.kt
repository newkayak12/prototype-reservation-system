package com.reservation.user.self.port.output

import com.reservation.enumeration.Role

fun interface CheckGeneralUserLoginIdDuplicated {
    fun query(inquiry: CheckGeneralUserDuplicatedInquiry): Boolean

    data class CheckGeneralUserDuplicatedInquiry(
        val loginId: String,
        val role: Role,
    )
}
