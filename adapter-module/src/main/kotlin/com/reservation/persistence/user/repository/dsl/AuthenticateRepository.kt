package com.reservation.persistence.user.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import com.reservation.user.self.port.output.AuthenticateGeneralUser
import com.reservation.user.self.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserInquiry
import com.reservation.user.self.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserResult
import org.springframework.stereotype.Component

@Component
class AuthenticateRepository(
    private val query: JPAQueryFactory,
) : AuthenticateGeneralUser {
    override fun query(request: AuthenticateGeneralUserInquiry): AuthenticateGeneralUserResult? {
        return query.select(
            Projections.constructor(
                AuthenticateGeneralUserResult::class.java,
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
