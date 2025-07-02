package com.reservation.user.resign.port.output

import com.reservation.user.self.User
import com.reservation.user.shared.LoginId
import com.reservation.user.shared.Password
import com.reservation.user.shared.PersonalAttributes
import java.time.LocalDateTime

fun interface LoadResignTargetUser {
    fun load(id: String): LoadResignTargetResult?

    data class LoadResignTargetResult(
        val id: String,
        val loginId: String,
        val encodedPassword: String,
        val oldEncodedPassword: String?,
        val changedDateTime: LocalDateTime?,
        val email: String,
        val mobile: String,
        val nickname: String,
    ) {
        fun toDomain(): User =
            User(
                id = id,
                loginId = LoginId(loginId),
                password =
                    Password(
                        encodedPassword = encodedPassword,
                        oldEncodedPassword = oldEncodedPassword,
                        changedDateTime = changedDateTime,
                    ),
                personalAttributes =
                    PersonalAttributes(
                        email = email,
                        mobile = mobile,
                    ),
                nickname = nickname,
            )
    }
}
