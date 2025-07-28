package com.reservation.authenticate.port.input

interface ExtractIdentifierFromHeaderUseCase {
    fun execute(authorization: String?): String
}
