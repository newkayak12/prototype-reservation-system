package com.reservation.category.tag.usecase

import com.reservation.category.tag.port.input.FindTagsUseCase
import com.reservation.category.tag.port.input.query.request.FindTagsQuery
import com.reservation.category.tag.port.input.query.response.FindTagsQueryResult
import com.reservation.category.tag.port.output.FindTags
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindTagsService(
    val findTags: FindTags,
) : FindTagsUseCase {
    @Transactional
    override fun execute(request: FindTagsQuery): List<FindTagsQueryResult> {
        return findTags.query(request.toInquiry()).map {
            FindTagsQueryResult(
                it.id,
                it.title,
                it.categoryType,
            )
        }
    }
}
