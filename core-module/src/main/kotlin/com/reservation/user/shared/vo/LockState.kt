package com.reservation.user.shared.vo

import com.reservation.enumeration.UserStatus
import com.reservation.enumeration.UserStatus.ACTIVATED
import com.reservation.enumeration.UserStatus.DEACTIVATED
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
    ): Boolean = lockedDateTime?.plus(interval, unit)?.isBefore(LocalDateTime.now()) ?: true

    fun deactivate(): LockState = LockState(failCount, LocalDateTime.now(), DEACTIVATED)

    fun activate(): LockState = LockState(0, null, ACTIVATED)

    fun addFailureCount(): LockState = LockState(failCount + 1, lockedDateTime, userStatus)
}
