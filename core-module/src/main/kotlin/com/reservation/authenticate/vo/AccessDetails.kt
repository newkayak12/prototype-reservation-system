package com.reservation.authenticate.vo

import com.reservation.enumeration.AccessStatus
import java.time.LocalDateTime

data class AccessDetails(
    val accessStatus: AccessStatus,
    val accessDateTime: LocalDateTime
) {

    companion object {
        fun accessGranted(): AccessDetails
        = AccessDetails(AccessStatus.SUCCESS, LocalDateTime.now())

        fun accessDenied(): AccessDetails
        = AccessDetails(AccessStatus.FAILURE, LocalDateTime.now())

    }
}
