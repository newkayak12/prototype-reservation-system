package com.reservation.rest.user.general.response

import com.reservation.user.self.port.input.FindGeneralUserIdsQuery.FindGeneralUserIdQueryResult

data class FindGeneralUserIdsResponse(
    val userId: String,
) {
    companion object {
        fun from(result: FindGeneralUserIdQueryResult): FindGeneralUserIdsResponse =
            FindGeneralUserIdsResponse(result.userId)
    }
}
