package com.reservation.user.self.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.user.self.port.input.FindGeneralUserUseCase
import com.reservation.user.self.port.input.query.response.FindGeneralUserQueryResult
import com.reservation.user.self.port.output.FindGeneralUser
import com.reservation.user.self.port.output.FindGeneralUser.FindGeneralUserInquiry
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindGeneralUserService(
    val findGeneralUser: FindGeneralUser,
) : FindGeneralUserUseCase {
    @Transactional(readOnly = true)
    override fun execute(id: String): FindGeneralUserQueryResult {
        return findGeneralUser.query(FindGeneralUserInquiry(id))
            ?.let { it.toQuery() }
            ?: throw NoSuchPersistedElementException()
    }
}
