package com.reservation.seller.self.port.output

import com.reservation.authenticate.Authenticate
import com.reservation.enumeration.Role
import com.reservation.enumeration.UserStatus
import com.reservation.shared.user.LockState
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import java.time.LocalDateTime

/**
 * loginId, password, Role.RESTAURANT_OWNER로 쿼리하여 결과를 가지고 옵니다.ㅁ
 */
@FunctionalInterface
interface AuthenticateSellerUser {
    fun query(request: AuthenticateSellerUserInquiry): AuthenticateSellerUserResult?

    data class AuthenticateSellerUserInquiry(
        val loginId: String,
        val password: String,
    ) {
        val role = Role.RESTAURANT_OWNER
    }

    data class AuthenticateSellerUserResult(
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
