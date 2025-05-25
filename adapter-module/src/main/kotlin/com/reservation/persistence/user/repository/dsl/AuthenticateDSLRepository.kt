package com.reservation.persistence.user.repository.dsl

import com.reservation.user.port.output.AuthenticateGeneralUser
import com.reservation.user.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserInquiry
import com.reservation.user.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserResult
import org.springframework.stereotype.Component

@Component
class AuthenticateDSLRepository : AuthenticateGeneralUser {
    override fun query(request: AuthenticateGeneralUserInquiry): AuthenticateGeneralUserResult {
        TODO("Not yet implemented")
    }
}
