package com.reservation.persistence.user.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import com.reservation.user.self.port.output.FindGeneralUser
import com.reservation.user.self.port.output.FindGeneralUser.FindGeneralUserInquiry
import com.reservation.user.self.port.output.FindGeneralUser.FindGeneralUserResult
import org.springframework.stereotype.Component

@Component
class FindGeneralUserRepository(
    private val query: JPAQueryFactory,
) : FindGeneralUser {
    override fun query(inquiry: FindGeneralUserInquiry): FindGeneralUserResult? {
        return query
            .select(
                Projections.constructor(
                    FindGeneralUserResult::class.java,
                    userEntity.identifier,
                    userEntity.loginId,
                    userEntity.email,
                    userEntity.nickname,
                    userEntity.mobile,
                    userEntity.userStatus,
                ),
            )
            .from(userEntity)
            .where(
                UserQuerySpec.idEq(inquiry.id),
                UserQuerySpec.userStatusEq(inquiry.userStatus),
                UserQuerySpec.roleIsUser(inquiry.userRole),
            )
            .fetchOne()
    }
}
