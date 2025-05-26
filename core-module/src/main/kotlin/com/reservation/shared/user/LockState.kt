package com.reservation.shared.user

import com.reservation.enumeration.UserStatus
import java.time.LocalDateTime
import java.time.temporal.TemporalUnit

data class LockState(
    val failCount: Int,
    var lockedDateTime: LocalDateTime?,
    val userStatus: UserStatus,
) {
    fun hasExceededFailCount(limit: Int): Boolean = failCount >= limit

    fun isDeactivated(): Boolean = userStatus.isDeactivated()

    fun isActivated(): Boolean = userStatus.isActivated()

    fun isLockdownTimeOver(
        interval: Long,
        unit: TemporalUnit,
    ): Boolean = lockedDateTime?.plus(interval, unit)?.isBefore(LocalDateTime.now()) ?: false

    fun deactivate(): LockState = LockState(failCount, LocalDateTime.now(), UserStatus.DEACTIVATED)

    fun activate(): LockState = LockState(0, null, UserStatus.ACTIVATED)

    fun addFailureCount(): LockState = LockState(failCount + 1, lockedDateTime, userStatus)
}
