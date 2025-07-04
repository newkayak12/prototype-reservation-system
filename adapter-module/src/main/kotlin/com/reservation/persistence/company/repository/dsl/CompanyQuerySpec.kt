package com.reservation.persistence.company.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.persistence.company.entity.QCompanyEntity.companyEntity
import org.springframework.util.StringUtils

object CompanyQuerySpec {
    fun brandNameContains(searchText: String?): BooleanExpression? =
        searchText?.let {
            if (StringUtils.hasText(it)) {
                return companyEntity.brandName.contains(it)
            }

            return null
        }
}
