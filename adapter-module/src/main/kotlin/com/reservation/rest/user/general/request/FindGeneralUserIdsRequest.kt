package com.reservation.rest.user.general.request

import com.reservation.user.self.port.input.query.request.FindGeneralUserIdQuery

data class FindGeneralUserIdsRequest(val email: String) {
    fun toQuery(): FindGeneralUserIdQuery = FindGeneralUserIdQuery(email)
}
