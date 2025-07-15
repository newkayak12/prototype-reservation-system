package com.reservation.persistence.user.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.reservation.enumeration.Role
import com.reservation.enumeration.UserStatus
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import org.springframework.util.StringUtils

object UserQuerySpec {
    fun idEq(id: String?): BooleanExpression? =
        id.let {
            if (StringUtils.hasText(id)) {
                return@let userEntity.identifier.eq(it)
            }

            return@let null
        } ?: null

    fun userStatusEq(userStatus: UserStatus?): BooleanExpression? =
        userStatus.let {
            userEntity.userStatus.eq(it)
        } ?: null

    fun loginIdEq(loginId: String?): BooleanExpression? =
        if (StringUtils.hasText(loginId)) userEntity.loginId.eq(loginId) else null

    fun emailLike(email: String?): BooleanExpression? =
        if (StringUtils.hasText(email)) userEntity.email.contains(email) else null

    fun nicknameEq(nickname: String?): BooleanExpression? =
        if (StringUtils.hasText(nickname)) userEntity.nickname.eq(nickname) else null

    fun roleIsUser(role: Role?): BooleanExpression? =
        role.let {
            userEntity.role.eq(role)
        } ?: Expressions.FALSE
}
