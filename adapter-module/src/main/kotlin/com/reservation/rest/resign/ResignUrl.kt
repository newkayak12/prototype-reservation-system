package com.reservation.rest.resign

object ResignUrl {
    const val PREFIX = "/api/v1/resign"
    const val RESIGN = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"
}
