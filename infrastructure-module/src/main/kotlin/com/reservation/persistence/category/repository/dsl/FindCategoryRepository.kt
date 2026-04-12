package com.reservation.persistence.category.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.category.cuisine.port.output.FindCuisines
import com.reservation.category.cuisine.port.output.FindCuisines.FindCuisinesInquiry
import com.reservation.category.cuisine.port.output.FindCuisines.FindCuisinesResult
import com.reservation.category.nationality.port.output.FindNationalities
import com.reservation.category.nationality.port.output.FindNationalities.FindNationalitiesInquiry
import com.reservation.category.nationality.port.output.FindNationalities.FindNationalitiesResult
import com.reservation.category.tag.port.output.FindTags
import com.reservation.category.tag.port.output.FindTags.FindTagsInquiry
import com.reservation.category.tag.port.output.FindTags.FindTagsResult
import com.reservation.enumeration.CategoryType
import com.reservation.enumeration.CategoryType.NATIONALITY
import com.reservation.persistence.category.entity.QCategoryEntity.categoryEntity
import org.springframework.stereotype.Component

@Component
class FindCategoryRepository(
    private val query: JPAQueryFactory,
) : FindNationalities, FindCuisines, FindTags {
    override fun query(inquiry: FindNationalitiesInquiry): List<FindNationalitiesResult> {
        val databaseInquiry =
            Inquiry(
                inquiry.ids?.toSet(),
                inquiry.title,
                inquiry.categoryType,
            )
        return queryToDatabase(databaseInquiry).map {
            FindNationalitiesResult(
                it.id,
                it.title,
                it.categoryType,
            )
        }
    }

    override fun query(inquiry: FindCuisinesInquiry): List<FindCuisinesResult> {
        val databaseInquiry =
            Inquiry(
                inquiry.ids?.toSet(),
                inquiry.title,
                inquiry.categoryType,
            )

        return queryToDatabase(databaseInquiry).map {
            FindCuisinesResult(
                it.id,
                it.title,
                it.categoryType,
            )
        }
    }

    override fun query(inquiry: FindTagsInquiry): List<FindTagsResult> {
        val databaseInquiry =
            Inquiry(
                inquiry.ids?.toSet(),
                inquiry.title,
                inquiry.categoryType,
            )

        return queryToDatabase(databaseInquiry).map {
            FindTagsResult(
                it.id,
                it.title,
                it.categoryType,
            )
        }
    }

    private fun queryToDatabase(inquiry: Inquiry): List<QueryResult> {
        return query
            .select(
                Projections.constructor(
                    QueryResult::class.java,
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
                CategoryQuerySpec.idsIn(inquiry.ids),
            )
            .fetch()
    }

    data class Inquiry(
        val ids: Set<Long>?,
        val title: String?,
        val categoryType: CategoryType = NATIONALITY,
    )

    data class QueryResult(
        val id: Long,
        val title: String,
        val categoryType: CategoryType,
    )
}
