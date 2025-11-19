package com.reservation.rest.timetable

import com.reservation.rest.common.response.ListResponse
import com.reservation.rest.timetable.request.FindTimeTableRequest
import com.reservation.rest.timetable.response.FindTimeTableResponse
import com.reservation.timetable.port.input.FindTimeTablesUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FindTimeTableController(
    private val findTimeTablesUseCase: FindTimeTablesUseCase,
) {
    @GetMapping(TimeTableUrl.FINDS)
    fun findTimeTables(
        findTimeTableRequest: FindTimeTableRequest,
    ): ListResponse<FindTimeTableResponse> =
        ListResponse.ok(
            findTimeTablesUseCase.execute(findTimeTableRequest.toQuery())
                .map { FindTimeTableResponse.from(it) },
        )
}
