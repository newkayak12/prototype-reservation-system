package com.reservation.category.nationality.port.input.query.request

import com.reservation.category.nationality.port.output.FindNationalities.FindNationalitiesInquiry

data class FindNationalitiesByTitleQuery(
    val title: String?,
) {
    fun toInquiry() = FindNationalitiesInquiry(null, title)
}
