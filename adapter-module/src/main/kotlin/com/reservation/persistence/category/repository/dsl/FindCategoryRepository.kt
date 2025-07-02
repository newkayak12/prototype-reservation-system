package com.reservation.persistence.category.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.category.nationality.port.output.FindNationalities
import com.reservation.category.nationality.port.output.FindNationalities.FindNationalitiesInquiry
import com.reservation.category.nationality.port.output.FindNationalities.FindNationalitiesResult
import com.reservation.persistence.category.entity.QCategoryEntity.categoryEntity
import org.springframework.stereotype.Component

@Component
class FindCategoryRepository(
    private val query: JPAQueryFactory,
) : FindNationalities {
    override fun query(inquiry: FindNationalitiesInquiry): List<FindNationalitiesResult> {
        return query
            .select(
                Projections.constructor(
                    FindNationalitiesResult::class.java,
                    categoryEntity.id,
                    categoryEntity.title,
                    categoryEntity.categoryType,
                ),
            )
            .from(categoryEntity)
            .where(
                CategoryQuerySpec.excludeDeleted(),
                CategoryQuerySpec.categoryTypeEqNationality(),
                CategoryQuerySpec.titleContains(inquiry.title),
            )
            .fetch()
    }
}
