package com.reservation.seller.self.port.input

import com.reservation.seller.self.port.output.AuthenticateSellerUser.AuthenticateSellerUserInquiry

/**
 * 아이디, 비밀번호로 Seller에 대한
 * 로그인을 진행합니다.
 */
@FunctionalInterface
interface AuthenticateSellerUserQuery {
    fun execute(request: SellerUserQueryDto): AuthenticateSellerUserQueryResult

    data class SellerUserQueryDto(
        val loginId: String,
        val password: String,
    ) {
        fun toInquiry(): AuthenticateSellerUserInquiry {
            return AuthenticateSellerUserInquiry(
                loginId,
                password,
            )
        }
    }

    data class AuthenticateSellerUserQueryResult(
        val accessToken: String,
        val refreshToken: String,
        val refreshTokenExpiresIn: Long,
    )
}
