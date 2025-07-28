package com.reservation.user.self.port.input

import com.reservation.user.self.port.input.query.request.FindGeneralUserIdQuery
import com.reservation.user.self.port.input.query.response.FindGeneralUserIdQueryResult

/**
 * 일반 사용자에 아이디 찾기를 요구합니다.
 * 조건에 맞는 아이디들을 찾고 이를 리턴합니다.
 */
interface FindGeneralUserIdsUseCase {
    fun execute(query: FindGeneralUserIdQuery): List<FindGeneralUserIdQueryResult>
}
