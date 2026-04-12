package com.reservation.persistence.category.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.reservation.enumeration.CategoryType.NATIONALITY
import com.reservation.persistence.category.entity.QCategoryEntity.categoryEntity
import org.springframework.util.StringUtils

object CategoryQuerySpec {
    fun excludeDeleted(): BooleanExpression = categoryEntity.logicalDelete.isDeleted

    fun categoryTypeEqNationality(): BooleanExpression = categoryEntity.categoryType.eq(NATIONALITY)

    fun titleContains(searchText: String?): BooleanExpression? =
        searchText?.let {
            if (StringUtils.hasText(it)) {
                return categoryEntity.title.contains(it)
            }
            return null
        }

    fun idsIn(ids: Set<Long>?): BooleanExpression? =
        ids?.let {
            if (ids.isNotEmpty()) {
                categoryEntity.id.`in`(ids)
            } else {
                null
            }
        }
}
