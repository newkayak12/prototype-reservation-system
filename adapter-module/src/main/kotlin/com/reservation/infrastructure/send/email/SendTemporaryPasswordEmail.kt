package com.reservation.infrastructure.send.email

import com.reservation.user.self.port.output.SendFindGeneralUserPasswordAsEmail
import com.reservation.user.self.port.output.SendFindGeneralUserPasswordAsEmail.FindGeneralUserPasswordEmailForm
import org.springframework.stereotype.Component

@Component
class SendTemporaryPasswordEmail : SendFindGeneralUserPasswordAsEmail {
    override fun execute(form: FindGeneralUserPasswordEmailForm) {
        TODO("Implementation is not planned")
    }
}
