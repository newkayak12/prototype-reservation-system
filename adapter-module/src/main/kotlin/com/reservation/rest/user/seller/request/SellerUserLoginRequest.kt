package com.reservation.rest.user.seller.request

import com.reservation.authenticate.port.input.query.request.SellerUserQuery
import jakarta.validation.constraints.NotBlank

data class SellerUserLoginRequest(
    @field:NotBlank
    val loginId: String,
    @field:NotBlank
    val password: String,
) {
    fun toQuery(): SellerUserQuery {
        return SellerUserQuery(loginId, password)
    }
}
