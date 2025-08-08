package com.reservation.rest.category.request

import com.reservation.category.nationality.port.input.query.request.FindNationalitiesByTitleQuery

data class FindNationalitiesRequest(val title: String?) {
    fun toQuery() = FindNationalitiesByTitleQuery(title)
}
