package com.reservation.httpinterface.timetable

import com.reservation.httpinterface.timetable.response.FindTimeTableOccupancyInternallyHttpInterfaceResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange

@HttpExchange
interface FindTimeTableOccupancyHttpInterface {
    @GetExchange
    fun findTimeTableOccupancyInternally(
        @PathVariable(name = "timeTableId") timeTableId: String,
        @PathVariable(name = "timeTableOccupancyId") timeTableOccupancyId: String,
    ): ResponseEntity<FindTimeTableOccupancyInternallyHttpInterfaceResponse>
}
