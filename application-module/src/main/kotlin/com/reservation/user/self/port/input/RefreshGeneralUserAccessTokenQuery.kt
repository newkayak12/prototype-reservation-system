package com.reservation.user.self.port.input

/**
 * refreshToken을 바탕으로 새로운 accessToken과 refresehToken을 갱신합니다.
 * refreshToken이 유효하지 않다고 판단하면 Exception을 발생시킵니다.
 * 1. [com.reservation.exceptions.UnauthorizedException]: 토큰이 잘못됐을 경우
 * 2. [com.reservation.exceptions.AlreadyExpiredException]: 이미 만료된 경우
 * 3. [com.reservation.exceptions.InvalidTokenException]: 보유하고 있는 토큰 정보와 상이할 경우
 */
@FunctionalInterface
interface RefreshGeneralUserAccessTokenQuery {
    fun refresh(refreshToken: String): RefreshResult

    data class RefreshResult(val accessToken: String, val refreshToken: String)
}
