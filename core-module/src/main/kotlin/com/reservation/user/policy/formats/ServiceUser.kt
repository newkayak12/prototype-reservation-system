package com.reservation.user.policy.formats

import com.reservation.user.policy.availables.PasswordChangeable
import com.reservation.user.policy.availables.PersonalAttributesChangeable
import com.reservation.user.policy.availables.UserWithdrawable

interface ServiceUser : PasswordChangeable, PersonalAttributesChangeable, UserWithdrawable {
    val identifier: String?
}
