package com.reservation.user.policy.formats

import com.reservation.user.policy.availables.PasswordChangeable
import com.reservation.user.policy.availables.PersonalAttributesChangeable
import com.reservation.user.policy.availables.UserAttributeChangeable
import com.reservation.user.policy.availables.UserResignable

interface ServiceUser :
    PasswordChangeable,
    PersonalAttributesChangeable,
    UserResignable,
    UserAttributeChangeable {
    val identifier: String?
}
