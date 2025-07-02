package com.reservation.rest.category.request

import com.reservation.category.nationality.port.input.FindNationalitiesQuery.FindNationalitiesQueryDto

data class FindNationalitiesRequest(val title: String?) {
    fun toQuery() = FindNationalitiesQueryDto(title)
}
