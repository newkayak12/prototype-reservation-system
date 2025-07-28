package com.reservation.authenticate.port.input

import com.reservation.authenticate.port.input.query.request.GeneralUserQuery
import com.reservation.authenticate.port.input.query.response.AuthenticateGeneralUserQueryResult

/**
 * 로그인을 위한 사용자를 조회합니다.
 */
interface AuthenticateGeneralUserUseCase {
    fun execute(request: GeneralUserQuery): AuthenticateGeneralUserQueryResult
}
