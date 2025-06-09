package com.reservation.user.self.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.user.self.port.input.FindGeneralUserIdQuery
import com.reservation.user.self.port.input.FindGeneralUserIdQuery.FindGeneralUserIdQueryDto
import com.reservation.user.self.port.input.FindGeneralUserIdQuery.FindGeneralUserIdQueryResult
import com.reservation.user.self.port.output.FindGeneralUserId
import com.reservation.utilities.mosaic.MaskingUtility

@UseCase
class FindGeneralUserIdUseCase(
    val findGeneralUserId: FindGeneralUserId,
) : FindGeneralUserIdQuery {
    override fun execute(query: FindGeneralUserIdQueryDto): List<FindGeneralUserIdQueryResult> {
        return findGeneralUserId.findGeneralUserId(query.toInquiry())
            .map {
                FindGeneralUserIdQueryResult(MaskingUtility.manipulate(it.userId))
            }
    }
}
