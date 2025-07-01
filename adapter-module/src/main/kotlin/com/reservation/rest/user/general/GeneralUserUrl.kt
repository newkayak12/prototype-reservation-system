package com.reservation.rest.user.general

object GeneralUserUrl {
    const val PREFIX = "/api/v1/user"
    const val USER_SIGN_UP = "$PREFIX/sign-up"
    const val USER_SIGN_IN = "$PREFIX/sign-in"
    const val USER_SIGN_OUT = "$PREFIX/sign-out"
    const val CHANGE_PASSWORD = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}/password"
    const val CHANGE_NICKNAME = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}/nickname"
    const val FIND_LOST_LOGIN_ID = "$PREFIX/find/login-id"
    const val FIND_LOST_PASSWORD = "$PREFIX/find/password"
    const val FIND_USER = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"
    const val REFRESH = "$PREFIX/refresh"
}
