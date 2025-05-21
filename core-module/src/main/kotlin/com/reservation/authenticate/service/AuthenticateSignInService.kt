package com.reservation.authenticate.service

import com.reservation.authenticate.Authenticate
import com.reservation.authenticate.policy.NormalSignInPolicyPolicy
import java.time.temporal.ChronoUnit

class AuthenticateSignInService {

    companion object {
        private const val LIMIT_COUNT = 5
        private const val INTERVAL = 30L
    }


    fun signIn(authenticate: Authenticate, rawPassword: String): Authenticate {
        val signInPolicy = NormalSignInPolicyPolicy(LIMIT_COUNT, INTERVAL, ChronoUnit.MINUTES)
        val isSucceed = authenticate.canISignIn(rawPassword, signInPolicy)

        val history = if (isSucceed) authenticate.accessGranted()
        else authenticate.accessDenied()

        authenticate.addHistory(history)

        return authenticate
    }
}
