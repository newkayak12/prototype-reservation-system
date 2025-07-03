package com.reservation.authenticate

import com.reservation.authenticate.vo.AccessDetails
import com.reservation.enumeration.AccessStatus
import com.reservation.user.shared.vo.LoginId
import java.time.LocalDateTime

class AccessHistory(
    val authenticateId: String,
    val loginId: LoginId,
    val accessDetails: AccessDetails,
) {
    companion object {
        fun success(
            id: String,
            loginId: LoginId,
        ): AccessHistory = AccessHistory(id, loginId, AccessDetails.accessGranted())

        fun failure(
            id: String,
            loginId: LoginId,
        ): AccessHistory = AccessHistory(id, loginId, AccessDetails.accessDenied())
    }

    fun accessStatus(): AccessStatus = accessDetails.accessStatus

    fun accessDateTime(): LocalDateTime = accessDetails.accessDateTime
}
