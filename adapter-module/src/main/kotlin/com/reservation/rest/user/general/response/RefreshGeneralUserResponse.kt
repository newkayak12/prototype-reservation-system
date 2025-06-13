package com.reservation.rest.user.general.response

data class RefreshGeneralUserResponse(
    val accessToken: String,
) {
    companion object {
        fun from(result: String): RefreshGeneralUserResponse {
            return RefreshGeneralUserResponse(result)
        }
    }
}
