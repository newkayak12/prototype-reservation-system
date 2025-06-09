package com.reservation.user.self.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.user.self.port.input.FindGeneralUserIdsQuery
import com.reservation.user.self.port.input.FindGeneralUserIdsQuery.FindGeneralUserIdQueryDto
import com.reservation.user.self.port.input.FindGeneralUserIdsQuery.FindGeneralUserIdQueryResult
import com.reservation.user.self.port.output.FindGeneralUserIds
import com.reservation.utilities.mosaic.MaskingUtility

@UseCase
class FindGeneralUserIdsUseCase(
    private val findGeneralUserIds: FindGeneralUserIds,
) : FindGeneralUserIdsQuery {
    override fun execute(query: FindGeneralUserIdQueryDto): List<FindGeneralUserIdQueryResult> {
        return findGeneralUserIds.findGeneralUserId(query.toInquiry())
            .map {
                FindGeneralUserIdQueryResult(MaskingUtility.manipulate(it.userId))
            }
    }
}
