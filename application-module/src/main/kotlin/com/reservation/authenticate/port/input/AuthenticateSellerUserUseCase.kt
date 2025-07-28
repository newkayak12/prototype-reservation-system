package com.reservation.authenticate.port.input

import com.reservation.authenticate.port.input.query.request.SellerUserQueryDto
import com.reservation.authenticate.port.input.query.response.AuthenticateSellerUserQueryResult

/**
 * 아이디, 비밀번호로 Seller에 대한
 * 로그인을 진행합니다.
 */
interface AuthenticateSellerUserUseCase {
    fun execute(request: SellerUserQueryDto): AuthenticateSellerUserQueryResult
}
