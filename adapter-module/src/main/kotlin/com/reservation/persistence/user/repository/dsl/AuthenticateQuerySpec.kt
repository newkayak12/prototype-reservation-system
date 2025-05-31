package com.reservation.persistence.user.repository.dsl

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.Expressions
import com.reservation.enumeration.Role
import com.reservation.persistence.user.entity.QUserEntity.userEntity

object AuthenticateQuerySpec {
    fun loginIdEq(loginId: String?): BooleanExpression? {
        return loginId.let {
            userEntity.loginId.eq(loginId)
        } ?: null
    }

    fun roleIsUser(role: Role?): BooleanExpression? {
        return role.let {
            userEntity.role.eq(role)
        } ?: Expressions.FALSE
    }
}
