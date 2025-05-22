package com.reservation.authenticate.service

import com.reservation.authenticate.Authenticate
import com.reservation.authenticate.policy.NormalSignInPolicy
import java.time.temporal.ChronoUnit

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
            NormalSignInPolicy(SIGN_IN_ATTEMPT_LIMIT, SIGN_IN_INTERVAL_MINUTES, ChronoUnit.MINUTES)
        val isSucceed = authenticate.canISignIn(rawPassword, signInPolicy)

        val history =
            if (isSucceed) {
                authenticate.accessGranted()
            } else {
                authenticate.accessDenied()
            }

        authenticate.addHistory(history)

        return authenticate
    }
}
