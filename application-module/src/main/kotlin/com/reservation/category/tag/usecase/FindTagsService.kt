package com.reservation.category.tag.usecase

import com.reservation.category.tag.port.input.FindTagsByIdsUseCase
import com.reservation.category.tag.port.input.FindTagsByTitleUseCase
import com.reservation.category.tag.port.input.query.request.FindTagsByIdsQuery
import com.reservation.category.tag.port.input.query.request.FindTagsByTitleQuery
import com.reservation.category.tag.port.input.query.response.FindTagsQueryResult
import com.reservation.category.tag.port.output.FindTags
import com.reservation.config.annotations.UseCase
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindTagsService(
    val findTags: FindTags,
) : FindTagsByTitleUseCase, FindTagsByIdsUseCase {
    @Transactional(readOnly = true)
    override fun execute(request: FindTagsByTitleQuery): List<FindTagsQueryResult> {
        return findTags.query(request.toInquiry()).map {
            FindTagsQueryResult(
                it.id,
                it.title,
                it.categoryType,
            )
        }
    }

    @Transactional(readOnly = true)
    override fun execute(request: FindTagsByIdsQuery): List<FindTagsQueryResult> {
        return findTags.query(request.toInquiry()).map {
            FindTagsQueryResult(
                it.id,
                it.title,
                it.categoryType,
            )
        }
    }
}
