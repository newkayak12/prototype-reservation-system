package com.reservation.authenticate.policy

import java.time.temporal.TemporalUnit

interface SignInPolicy {
    fun limitCount(): Int

    fun interval(): Long

    fun unit(): TemporalUnit
}
