package com.reservation.persistence.user.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.reservation.enumeration.Role
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import org.springframework.util.StringUtils

object UserQuerySpec {
    fun loginIdEq(loginId: String?): BooleanExpression? =
        loginId.let {
            if (StringUtils.hasText(it)) {
                return@let userEntity.loginId.eq(it)
            }

            return@let null
        } ?: null

    fun emailLike(email: String?): BooleanExpression? =
        email.let {
            if (StringUtils.hasText(it)) {
                return@let userEntity.email.contains(it)
            }

            return@let null
        } ?: null

    fun nicknameEq(nickname: String?): BooleanExpression? =
        nickname.let {
            if (StringUtils.hasText(it)) {
                return@let userEntity.nickname.eq(it)
            }

            return@let null
        } ?: null

    fun roleIsUser(role: Role?): BooleanExpression? =
        role.let {
            userEntity.role.eq(role)
        } ?: Expressions.FALSE
}
