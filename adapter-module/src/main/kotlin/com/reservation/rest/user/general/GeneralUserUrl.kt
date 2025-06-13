package com.reservation.rest.user.general

object GeneralUserUrl {
    const val USER_SIGN_UP = "/api/v1/user/sign-up"
    const val USER_SIGN_IN = "/api/v1/user/sign-in"
    const val USER_SIGN_OUT = "/api/v1/user/sign-out"
    const val CHANGE_PASSWORD = "/api/v1/user/{id:[0-9a-fA-F\\-]{36}}/password"
    const val CHANGE_NICKNAME = "/api/v1/user/{id:[0-9a-fA-F\\-]{36}}/nickname"
    const val FIND_LOST_LOGIN_ID = "/api/v1/user/find/login-id"
    const val FIND_LOST_PASSWORD = "/api/v1/user/find/password"
    const val FIND_USER = "/api/v1/user/{id:[0-9a-fA-F\\-]{36}}"
    const val REFRESH = "/api/v1/user/refresh"
}
