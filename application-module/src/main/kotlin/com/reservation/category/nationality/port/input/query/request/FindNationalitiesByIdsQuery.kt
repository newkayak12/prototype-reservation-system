package com.reservation.category.nationality.port.input.query.request

import com.reservation.category.nationality.port.output.FindNationalities.FindNationalitiesInquiry

data class FindNationalitiesByIdsQuery(
    val ids: List<Long>?,
) {
    fun toInquiry() = FindNationalitiesInquiry(ids, null)
}
