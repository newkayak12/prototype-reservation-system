package com.reservation.persistence.user.repository.dsl

import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import com.reservation.user.self.port.output.CheckGeneralUserDuplicated
import com.reservation.user.self.port.output.CheckGeneralUserDuplicated.CheckGeneralUserDuplicatedInquiry
import org.springframework.stereotype.Component

@Component
class CheckDuplicatedGeneralUserRepository(
    private val query: JPAQueryFactory,
) : CheckGeneralUserDuplicated {
    override fun query(inquiry: CheckGeneralUserDuplicatedInquiry): Boolean {
        return query.select(
            userEntity.id,
        )
            .from(userEntity)
            .where(
                UserQuerySpec.roleIsUser(inquiry.role),
                UserQuerySpec.loginIdEq(inquiry.loginId),
            )
            .fetchFirst() != null
    }
}
