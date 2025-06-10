package com.reservation.user.self.port.output

import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.user.self.User
import java.time.LocalDateTime

interface LoadGeneralUserByLoginIdAndEmail {
    fun load(
        inquiry: LoadGeneralUserByLoginIdAndEmailInquiry,
    ): LoadGeneralUserByLoginIdAndEmailResult?

    data class LoadGeneralUserByLoginIdAndEmailInquiry(
        val loginId: String,
        val email: String,
    )

    data class LoadGeneralUserByLoginIdAndEmailResult(
        val id: String,
        val loginId: String,
        val encodedPassword: String,
        val oldEncodedPassword: String?,
        val changedDateTime: LocalDateTime?,
        val isNeedToChangePassword: Boolean,
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
                        isNeedToChangePassword = isNeedToChangePassword,
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
