package com.reservation.rest.menu

object MenuUrl {
    const val PREFIX = "/api/v1/menu"
    const val CREATE = PREFIX
    const val FIND_MENUS = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}/all"
    const val FIND_MENU = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"
    const val CHANGE = "$PREFIX/{id:[0-9a-fA-F\\-]{36}}"
}
