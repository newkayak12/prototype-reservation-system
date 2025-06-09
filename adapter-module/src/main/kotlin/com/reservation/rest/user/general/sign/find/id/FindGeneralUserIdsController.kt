package com.reservation.rest.user.general.sign.find.id

import com.reservation.rest.common.response.ListResponse
import com.reservation.rest.user.general.GeneralUserUrl
import com.reservation.rest.user.general.request.FindGeneralUserIdsRequest
import com.reservation.rest.user.general.response.FindGeneralUserIdsResponse
import com.reservation.user.self.port.input.FindGeneralUserIdsQuery
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FindGeneralUserIdsController(
    private val findGeneralUserIdsQuery: FindGeneralUserIdsQuery,
) {
    @GetMapping(GeneralUserUrl.FIND_LOST_LOGIN_ID)
    fun findLostLoginId(
        request: FindGeneralUserIdsRequest,
    ): ListResponse<FindGeneralUserIdsResponse> =
        ListResponse.ok(
            findGeneralUserIdsQuery.execute(request.toQuery())
                .map {
                    FindGeneralUserIdsResponse.from(it)
                },
        )
}
