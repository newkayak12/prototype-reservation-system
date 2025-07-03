package com.reservation.category.tag.usecase

import com.reservation.category.tag.port.input.FindTagsQuery
import com.reservation.category.tag.port.input.FindTagsQuery.FindTagsQueryDto
import com.reservation.category.tag.port.input.FindTagsQuery.FindTagsQueryResult
import com.reservation.category.tag.port.output.FindTags
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindTagsUseCase(
    val findTags: FindTags,
) : FindTagsQuery {
    @Transactional
    override fun execute(request: FindTagsQueryDto): List<FindTagsQueryResult> {
        return findTags.query(request.toInquiry()).map {
            FindTagsQueryResult(
                it.id,
                it.title,
                it.categoryType,
            )
        }
    }
}
