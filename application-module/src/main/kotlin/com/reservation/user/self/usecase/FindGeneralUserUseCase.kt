package com.reservation.user.self.usecase

import com.reservation.config.annotations.UseCase
import com.reservation.user.exceptions.NoSuchDatabaseElementException
import com.reservation.user.self.port.input.FindGeneralUserQuery
import com.reservation.user.self.port.input.FindGeneralUserQuery.FindGeneralUserQueryResult
import com.reservation.user.self.port.output.FindGeneralUser
import com.reservation.user.self.port.output.FindGeneralUser.FindGeneralUserInquiry
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindGeneralUserUseCase(
    val findGeneralUser: FindGeneralUser,
) : FindGeneralUserQuery {
    @Transactional(readOnly = true)
    override fun execute(id: String): FindGeneralUserQueryResult {
        return findGeneralUser.query(FindGeneralUserInquiry(id))
            ?.let { it.toQuery() }
            ?: throw NoSuchDatabaseElementException()
    }
}
