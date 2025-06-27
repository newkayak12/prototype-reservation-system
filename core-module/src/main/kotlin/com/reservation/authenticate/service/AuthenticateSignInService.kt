package com.reservation.authenticate.service

import com.reservation.authenticate.Authenticate
import com.reservation.authenticate.policy.NormalSignInPolicy
import java.time.temporal.ChronoUnit

/**
 * 사용자의 로그인, 비밀번호 매치
 * 이에 따른 **성공**, **실패**를 다룹니다.
 * 이후 정책은 [NormalSignInPolicy]를 따릅니다.
 */
class AuthenticateSignInService {
    companion object {
        private const val SIGN_IN_ATTEMPT_LIMIT = 5
        private const val SIGN_IN_INTERVAL_MINUTES = 30L
    }

    fun signIn(
        authenticate: Authenticate,
        rawPassword: String,
    ): Authenticate {
        val signInPolicy =
            NormalSignInPolicy(
                SIGN_IN_ATTEMPT_LIMIT,
                SIGN_IN_INTERVAL_MINUTES,
                ChronoUnit.MINUTES,
            )
        authenticate.canISignIn(rawPassword, signInPolicy)
        return authenticate
    }
}
