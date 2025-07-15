package com.reservation.authenticate.port.input

fun interface ExtractIdentifierFromHeaderQuery {
    fun execute(authorization: String?): String
}
