package com.reservation.persistence.user.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.authenticate.port.output.AuthenticateGeneralUser
import com.reservation.authenticate.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserInquiry
import com.reservation.authenticate.port.output.AuthenticateGeneralUser.AuthenticateGeneralUserResult
import com.reservation.authenticate.port.output.AuthenticateSellerUser
import com.reservation.authenticate.port.output.AuthenticateSellerUser.AuthenticateSellerUserInquiry
import com.reservation.authenticate.port.output.AuthenticateSellerUser.AuthenticateSellerUserResult
import com.reservation.enumeration.Role
import com.reservation.enumeration.UserStatus
import com.reservation.persistence.user.entity.QUserEntity.userEntity
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class AuthenticateUserRepository(
    private val query: JPAQueryFactory,
) : AuthenticateGeneralUser, AuthenticateSellerUser {
    override fun query(request: AuthenticateGeneralUserInquiry): AuthenticateGeneralUserResult? {
        val inquiry =
            Inquiry(
                request.loginId,
                request.password,
                request.role,
            )

        return queryToDatabase(inquiry)?.let {
            AuthenticateGeneralUserResult(
                it.id,
                it.loginId,
                it.password,
                it.failCount,
                it.userStatus,
                it.lockedDatetime,
            )
        }
    }

    override fun query(request: AuthenticateSellerUserInquiry): AuthenticateSellerUserResult? {
        val inquiry =
            Inquiry(
                request.loginId,
                request.password,
                request.role,
            )
        return queryToDatabase(inquiry)?.let {
            AuthenticateSellerUserResult(
                it.id,
                it.loginId,
                it.password,
                it.failCount,
                it.userStatus,
                it.lockedDatetime,
            )
        }
    }

    private fun queryToDatabase(inquiry: Inquiry): Result? {
        return query.select(
            Projections.constructor(
                Result::class.java,
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
                UserQuerySpec.loginIdEq(inquiry.loginId),
                UserQuerySpec.roleIsUser(inquiry.role),
            )
            .fetchOne()
    }

    data class Inquiry(
        val loginId: String,
        val password: String,
        val role: Role,
    )

    data class Result(
        val id: String,
        val loginId: String,
        val password: String,
        val failCount: Int,
        val userStatus: UserStatus,
        val lockedDatetime: LocalDateTime?,
        val role: Role,
    )
}
