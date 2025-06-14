package com.reservation.user.self.port.output

import com.reservation.enumeration.Role

interface CheckGeneralUserLoginIdDuplicated {
    fun isDuplicated(inquiry: CheckGeneralUserDuplicatedInquiry): Boolean

    data class CheckGeneralUserDuplicatedInquiry(
        val loginId: String,
        val role: Role,
    )
}
