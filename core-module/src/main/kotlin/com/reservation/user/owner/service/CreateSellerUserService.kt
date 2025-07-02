package com.reservation.user.owner.service

import com.reservation.user.owner.RestaurantOwner
import com.reservation.user.policy.formats.CreateUserFormats
import com.reservation.user.shared.LoginId
import com.reservation.user.shared.Password
import com.reservation.user.shared.PersonalAttributes
import com.reservation.utilities.encrypt.password.PasswordEncoderUtility
import java.time.LocalDateTime

class CreateSellerUserService {
    fun <T : CreateUserFormats> createSellerUser(form: T): RestaurantOwner =
        RestaurantOwner(
            loginId = LoginId(form.loginId()),
            password =
                Password(
                    PasswordEncoderUtility.encode(form.password()),
                    null,
                    LocalDateTime.now(),
                ),
            personalAttributes =
                PersonalAttributes(
                    email = form.email(),
                    mobile = form.mobile(),
                ),
            nickname = form.nickname(),
        )
}
