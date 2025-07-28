package com.reservation.user.self.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.user.self.port.input.FindGeneralUserIdsUseCase
import com.reservation.user.self.port.input.query.request.FindGeneralUserIdQuery
import com.reservation.user.self.port.input.query.response.FindGeneralUserIdQueryResult
import com.reservation.user.self.port.output.FindGeneralUserIds
import com.reservation.utilities.mosaic.MaskingUtility
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindGeneralUserIdsService(
    private val findGeneralUserIds: FindGeneralUserIds,
) : FindGeneralUserIdsUseCase {
    @Transactional(readOnly = true)
    override fun execute(query: FindGeneralUserIdQuery): List<FindGeneralUserIdQueryResult> {
        return findGeneralUserIds.query(query.toInquiry())
            .map {
                FindGeneralUserIdQueryResult(MaskingUtility.manipulate(it.userId))
            }
    }
}
