package com.reservation.user.self.port.input

import com.reservation.user.self.port.input.query.response.FindGeneralUserQueryResult

/**
 * 일반 사용자의 정보 조회를 요구합니다.
 */
interface FindGeneralUserUseCase {
    fun execute(id: String): FindGeneralUserQueryResult
}
