package com.reservation.persistence.user.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.authenticate.port.output.AuthenticateSellerUser
import com.reservation.authenticate.port.output.AuthenticateSellerUser.AuthenticateSellerUserInquiry
import com.reservation.authenticate.port.output.AuthenticateSellerUser.AuthenticateSellerUserResult
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import org.springframework.stereotype.Component

@Component
class AuthenticateSellerUserRepository(
    private val query: JPAQueryFactory,
) : AuthenticateSellerUser {
    override fun query(request: AuthenticateSellerUserInquiry): AuthenticateSellerUserResult? {
        return query.select(
            Projections.constructor(
                AuthenticateSellerUserResult::class.java,
                userEntity.id,
                userEntity.loginId,
                userEntity.password,
                userEntity.failCount,
                userEntity.userStatus,
                userEntity.lockedDatetime,
            ),
        )
            .from(userEntity)
            .where(
                UserQuerySpec.loginIdEq(request.loginId),
                UserQuerySpec.roleIsUser(request.role),
            )
            .fetchOne()
    }
}
