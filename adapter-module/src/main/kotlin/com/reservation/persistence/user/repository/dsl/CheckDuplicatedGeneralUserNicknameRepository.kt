package com.reservation.persistence.user.repository.dsl

import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import com.reservation.user.self.port.output.CheckGeneralUserNicknameDuplicated
import com.reservation.user.self.port.output.CheckGeneralUserNicknameDuplicated.CheckGeneralUserNicknameDuplicatedInquiry
import org.springframework.stereotype.Component

@Component
class CheckDuplicatedGeneralUserNicknameRepository(
    private val query: JPAQueryFactory,
) : CheckGeneralUserNicknameDuplicated {
    override fun query(inquiry: CheckGeneralUserNicknameDuplicatedInquiry): Boolean {
        return query.select(
            userEntity.id,
        )
            .from(userEntity)
            .where(
                UserQuerySpec.roleIsUser(inquiry.role),
                UserQuerySpec.nicknameEq(inquiry.nickname),
            )
            .fetchFirst() != null
    }
}
