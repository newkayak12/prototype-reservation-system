package com.reservation.httpinterface.timetable

import com.reservation.httpinterface.timetable.response.FindTimeTableOccupancyInternallyHttpInterfaceResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.HttpExchange

@HttpExchange
interface FindTimeTableOccupancyHttpInterface {
    companion object {
        const val TIME_TABLE = "{timeTableId:[0-9a-fA-F\\-]{36}}"
        const val TIME_TABLE_OCCUPANCY = "{timeTableOccupancyId:[0-9a-fA-F\\-]{36}}"
        const val URL = "/api/v1/internal/timetable/$TIME_TABLE/occupancy/$TIME_TABLE_OCCUPANCY"
    }

    @GetExchange(URL)
    @ResponseStatus(HttpStatus.CREATED)
    fun findTimeTableOccupancyInternally(
        @PathVariable(name = "timeTableId") timeTableId: String,
        @PathVariable(name = "timeTableOccupancyId") timeTableOccupancyId: String,
    ): ResponseEntity<FindTimeTableOccupancyInternallyHttpInterfaceResponse>
}
