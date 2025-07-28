package com.reservation.rest.category.request

import com.reservation.category.nationality.port.input.query.request.FindNationalitiesQuery

data class FindNationalitiesRequest(val title: String?) {
    fun toQuery() = FindNationalitiesQuery(title)
}
