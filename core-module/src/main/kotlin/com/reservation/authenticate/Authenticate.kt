package com.reservation.authenticate

import com.reservation.authenticate.policy.SignInPolicy
import com.reservation.enumeration.Role
import com.reservation.enumeration.UserStatus
import com.reservation.user.shared.vo.LockState
import com.reservation.user.shared.vo.LoginId
import com.reservation.user.shared.vo.Password
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import java.time.LocalDateTime
import java.time.temporal.TemporalUnit

class Authenticate(
    val id: String,
    private val loginId: LoginId,
    private val password: Password,
    private var lockState: LockState,
    val role: Role,
) {
    private val accessLog: MutableList<AccessHistory> = mutableListOf()
    var passwordCheckSuccess: Boolean = false
        private set
    var lockCheckSuccess: Boolean = false
        private set
    val isSuccess: Boolean
        get() = passwordCheckSuccess && lockCheckSuccess

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

    val accessHistories: List<AccessHistory>
        get() = accessLog.toList()

    private fun isPasswordSame(rawPassword: String): Boolean {
        return PasswordEncoderUtility.matches(rawPassword, password.encodedPassword).also {
            if (!it) {
                lockState = lockState.addFailureCount()
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
        passwordCheckSuccess = isPasswordSame(rawPassword)
        lockCheckSuccess = isLockdownTimeOver(signInPolicy.interval(), signInPolicy.unit())
        writeAccessHistory()

        if (passwordCheckSuccess && lockCheckSuccess) {
            lockState = lockState.activate()
            return
        }

        if (hasExceededFailCount(signInPolicy.limitCount())) {
            lockState = lockState.deactivate()
            lockCheckSuccess = false
        }
    }

    private fun writeAccessHistory() {
        val history =
            if (passwordCheckSuccess && lockCheckSuccess) {
                AccessHistory.success(id, loginId)
            } else {
                AccessHistory.failure(id, loginId)
            }

        accessLog.add(history)
    }

    fun loginId(): String = loginId.loginId
}
