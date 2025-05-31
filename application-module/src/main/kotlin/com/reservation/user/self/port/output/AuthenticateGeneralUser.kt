package com.reservation.user.self.port.output

import com.reservation.authenticate.Authenticate
import com.reservation.enumeration.Role
import com.reservation.enumeration.UserStatus
import com.reservation.shared.user.LockState
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import java.time.LocalDateTime

@FunctionalInterface
interface AuthenticateGeneralUser {
    fun query(request: AuthenticateGeneralUserInquiry): AuthenticateGeneralUserResult?

    data class AuthenticateGeneralUserInquiry(
        val loginId: String,
        val password: String,
    ) {
        val role = Role.USER
    }

    data class AuthenticateGeneralUserResult(
        private val id: String,
        private val loginId: String,
        private val password: String,
        private val failCount: Int,
        private val userStatus: UserStatus,
        private val lockedDatetime: LocalDateTime?,
    ) {
        private val role = Role.USER

        fun toDomain(): Authenticate {
            return Authenticate(
                id,
                LoginId(loginId),
                Password(password, null, null),
                LockState(failCount, lockedDatetime, userStatus),
                role,
            )
        }
    }
}
