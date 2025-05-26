package com.reservation.authenticate

import com.reservation.authenticate.policy.SignInPolicy
import com.reservation.encrypt.password.PasswordEncoderUtility
import com.reservation.enumeration.Role
import com.reservation.enumeration.UserStatus
import com.reservation.shared.user.LockState
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import java.time.LocalDateTime
import java.time.temporal.TemporalUnit

class Authenticate(
    val id: String,
    private val loginId: LoginId,
    private val password: Password,
    private val lockState: LockState,
    val role: Role,
) {
    private val accessLog: MutableList<AccessHistory> = mutableListOf()
    var isSuccess: Boolean = false
        private set

    private val isDeactivated: Boolean
        get() = lockState.isDeactivated()

    private val isActivated: Boolean
        get() = lockState.isActivated()

    val failCount: Int
        get() = lockState.failCount

    val lockedDateTime: LocalDateTime?
        get() = lockState.lockedDateTime

    val userStatus: UserStatus
        get() = lockState.userStatus

    private fun isPasswordSame(rawPassword: String): Boolean {
        return PasswordEncoderUtility.matches(rawPassword, password.encodedPassword).also {
            if (!it) {
                lockState.addFailureCount()
            }
        }
    }

    private fun hasExceededFailCount(limitCount: Int): Boolean =
        lockState.hasExceededFailCount(limitCount)

    private fun isLockdownTimeOver(
        interval: Long,
        unit: TemporalUnit,
    ): Boolean = lockState.isLockdownTimeOver(interval, unit)

    fun canISignIn(
        rawPassword: String,
        signInPolicy: SignInPolicy,
    ) {
        if (!isPasswordSame(rawPassword) || (
                isDeactivated &&
                    !isLockdownTimeOver(
                        signInPolicy.interval(),
                        signInPolicy.unit(),
                    )
            )
        ) {
            if (hasExceededFailCount(signInPolicy.limitCount())) {
                lockState.deactivate()
            }
            isSuccess = false
            return
        }

        if (isActivated) {
            lockState.activate()
        }

        isSuccess = true
        return
    }

    fun writeAccessHistory() {
        val history =
            if (isSuccess) {
                AccessHistory.success(id, loginId)
            } else {
                AccessHistory.failure(id, loginId)
            }
        accessLog.add(history)
    }

    fun accessHistories(): List<AccessHistory> = accessLog.toList()

    fun loginId(): String = loginId.loginId
}
