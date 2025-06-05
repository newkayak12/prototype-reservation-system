package com.reservation.rest.user.general

object GeneralUserUrl {
    const val USER_SIGN_UP = "/api/v1/user/sign-up"
    const val USER_SIGN_IN = "/api/v1/user/sign-in"
    const val USER_SIGN_OUT = "/api/v1/user/sign-out"
    const val CHANGE_PASSWORD = "/api/v1/user/{id:[0-9a-fA-F\\-]{36}}/change-password"
}
