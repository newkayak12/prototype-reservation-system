package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.FindGeneralUserIdsQuery.FindGeneralUserIdQueryDto

data class FindGeneralUserIdsRequest(val email: String) {
    fun toQuery(): FindGeneralUserIdQueryDto = FindGeneralUserIdQueryDto(email)
}
