package com.reservation.user.self.port.output

import com.reservation.enumeration.Role

@FunctionalInterface
interface CheckGeneralUserNicknameDuplicated {
    fun isDuplicated(inquiry: CheckGeneralUserNicknameDuplicatedInquiry): Boolean

    data class CheckGeneralUserNicknameDuplicatedInquiry(
        val nickname: String,
        val role: Role,
    )
}
