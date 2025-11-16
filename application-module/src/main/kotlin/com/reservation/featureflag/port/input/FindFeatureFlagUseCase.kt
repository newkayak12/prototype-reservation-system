package com.reservation.featureflag.port.input

import com.reservation.featureflag.port.input.query.request.FindFeatureFlagQuery
import com.reservation.featureflag.port.input.query.response.FindFeatureFlagQueryResult

interface FindFeatureFlagUseCase {

    fun execute(request: FindFeatureFlagQuery): FindFeatureFlagQueryResult
}
