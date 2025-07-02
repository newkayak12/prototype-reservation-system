package com.reservation.user.self.port.output

import com.reservation.enumeration.Role

fun interface CheckGeneralUserNicknameDuplicated {
    fun query(inquiry: CheckGeneralUserNicknameDuplicatedInquiry): Boolean

    data class CheckGeneralUserNicknameDuplicatedInquiry(
        val nickname: String,
        val role: Role,
    )
}
