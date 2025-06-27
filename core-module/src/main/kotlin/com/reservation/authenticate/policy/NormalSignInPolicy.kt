package com.reservation.authenticate.policy

import java.time.temporal.TemporalUnit

/**
 * 정해진 횟수를 초과하면 정해진 기간 동안 잠금 상태로 변경됩니다.
 * 이 이후 성공하면 해당 상태가 해제됩니다.
 */
data class NormalSignInPolicy(
    private val limitCount: Int,
    private val interval: Long,
    private val unit: TemporalUnit,
) : SignInPolicy {
    override fun limitCount(): Int = limitCount

    override fun interval(): Long = interval

    override fun unit(): TemporalUnit = unit
}
