package com.reservation.authenticate.policy

import java.time.temporal.TemporalUnit

data class NormalSignInPolicy(
    private val limitCount: Int,
    private val interval: Long,
    private val unit: TemporalUnit,
) : SignInPolicy {
    override fun limitCount(): Int = limitCount

    override fun interval(): Long = interval

    override fun unit(): TemporalUnit = unit
}
