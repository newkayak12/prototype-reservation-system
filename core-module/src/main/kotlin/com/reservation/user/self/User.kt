package com.reservation.user.self

import com.reservation.enumeration.Role
import com.reservation.resign.self.EncryptedAttributes
import com.reservation.resign.self.ResignedUser
import com.reservation.shared.user.LoginId
import com.reservation.shared.user.Password
import com.reservation.shared.user.PersonalAttributes
import com.reservation.shared.user.UserAttribute
import com.reservation.user.common.exceptions.ResignWithoutIdException
import com.reservation.user.policy.formats.ServiceUser
import java.time.LocalDateTime

/**
 * 일반 사용자를 표현합니다.
 */
class User(
    private val id: String? = null,
    private val loginId: LoginId,
    private var password: Password,
    private var personalAttributes: PersonalAttributes,
    nickname: String,
) : ServiceUser {
    private var userAttributes: UserAttribute = UserAttribute(nickname, Role.USER)

    override val identifier: String?
        get() = id
    val userLoginId: String
        get() = loginId.loginId

    val userEncodedPassword: String
        get() = userPasswordSet.encodedPassword
    val userOldEncodedPassword: String?
        get() = userPasswordSet.oldEncodedPassword
    val userPasswordChangedDatetime: LocalDateTime?
        get() = userPasswordSet.changedDateTime

    override val userEmail: String
        get() = personalAttributes.email
    override val userMobile: String
        get() = personalAttributes.mobile
    override val userNickname: String
        get() = userAttributes.nickname
    override val userRole: Role
        get() = userAttributes.role
    override val userPasswordSet: Password
        get() = password

    override fun personalAttributes(): PersonalAttributes = personalAttributes

    override fun userAttributes(): UserAttribute = userAttributes

    override fun resign(encryptedAttributes: EncryptedAttributes): ResignedUser {
        if (id == null) {
            throw ResignWithoutIdException()
        }
        return ResignedUser(
            id,
            loginId,
            encryptedAttributes,
            LocalDateTime.now(),
        )
    }

    override fun changeUserNickname(userAttributes: UserAttribute) {
        this.userAttributes = userAttributes
    }

    override fun changePassword(password: Password) {
        this.password = password
    }

    override fun changePersonalAttributes(personalAttributes: PersonalAttributes) {
        this.personalAttributes = personalAttributes
    }
}
