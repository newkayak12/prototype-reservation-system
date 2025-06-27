package com.reservation.persistence.user.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import com.reservation.user.self.port.output.FindGeneralUserIds
import com.reservation.user.self.port.output.FindGeneralUserIds.FindGeneralUserIdInquiry
import com.reservation.user.self.port.output.FindGeneralUserIds.FindGeneralUserIdResult
import org.springframework.stereotype.Component

@Component
class FindGeneralUserIdsRepository(
    private val query: JPAQueryFactory,
) : FindGeneralUserIds {
    override fun query(inquiry: FindGeneralUserIdInquiry): List<FindGeneralUserIdResult> =
        query.select(
            Projections.constructor(
                FindGeneralUserIdResult::class.java,
                userEntity.loginId,
            ),
        )
            .where(
                UserQuerySpec.emailLike(inquiry.email),
            )
            .from(userEntity).fetch()
}
