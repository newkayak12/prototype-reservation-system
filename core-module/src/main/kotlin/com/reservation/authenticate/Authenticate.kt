package com.reservation.authenticate

import com.reservation.authenticate.policy.SignInPolicy
import com.reservation.encrypt.password.PasswordEncoderUtility
import com.reservation.shared.user.LockState
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import java.time.temporal.TemporalUnit

class Authenticate(
    val id: String,
    private val loginId: LoginId,
    private val password: Password,
    private val lockState: LockState,
) {
    private val accessLog: MutableList<AccessHistory> = mutableListOf()


    fun addHistory(history: AccessHistory) {
        accessLog.add(history)
    }

    private fun isPasswordSame(rawPassword: String): Boolean {
        return PasswordEncoderUtility.matches(rawPassword, password.encodedPassword)
            .also {
                if(!it) {
                    lockState.addFailureCount()
                }
            }
    }

    private fun hasExceededFailCount(limitCount: Int): Boolean =
        lockState.hasExceededFailCount(limitCount)

    private fun isDeactivated(): Boolean = lockState.isDeactivated()
    private fun isActivated(): Boolean = lockState.isActivated()

    private fun isLockdownTimeOver(interval: Long, unit: TemporalUnit): Boolean =
        lockState.isLockdownTimeOver(interval, unit)

    fun canISignIn(rawPassword: String, signInPolicy: SignInPolicy): Boolean {
        if (
            !isPasswordSame(rawPassword) ||
            (isDeactivated() && !isLockdownTimeOver(signInPolicy.interval(), signInPolicy.unit()))
        ) {
            if (hasExceededFailCount(signInPolicy.limitCount())) {
                lockState.deactivate()
            }
            return false
        }


        if (isActivated()) {
            lockState.activate()
        }

        return true
    }


    fun accessGranted(): AccessHistory  = AccessHistory.success(id, loginId)
    fun accessDenied(): AccessHistory = AccessHistory.failure(id, loginId)
}
