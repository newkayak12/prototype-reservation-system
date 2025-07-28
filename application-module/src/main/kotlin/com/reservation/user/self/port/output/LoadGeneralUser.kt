package com.reservation.user.self.port.output

import com.reservation.user.self.User
import com.reservation.user.shared.vo.LoginId
import com.reservation.user.shared.vo.Password
import com.reservation.user.shared.vo.PersonalAttributes
import java.time.LocalDateTime

interface LoadGeneralUser {
    fun load(id: String): LoadGeneralUserResult?

    data class LoadGeneralUserResult(
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
