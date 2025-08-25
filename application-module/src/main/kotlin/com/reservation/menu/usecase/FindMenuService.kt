package com.reservation.menu.usecase

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.config.annotations.UseCase
import com.reservation.menu.port.input.FindMenuUseCase
import com.reservation.menu.port.input.response.FindMenuQueryResult
import com.reservation.menu.port.output.FindMenu
import org.springframework.transaction.annotation.Transactional

@UseCase
class FindMenuService(private val findMenu: FindMenu) : FindMenuUseCase {
    @Transactional(readOnly = true)
    override fun execute(id: String): FindMenuQueryResult {
        return findMenu.query(id)
            ?.let {
                FindMenuQueryResult(
                    id = it.identifier,
                    restaurantId = it.restaurantId,
                    title = it.title,
                    description = it.description,
                    price = it.price,
                    isRepresentative = it.isRepresentative,
                    isRecommended = it.isRecommended,
                    isVisible = it.isVisible,
                    photos = it.photos.map { photo -> photo.toQuery() },
                )
            }
            ?: throw NoSuchPersistedElementException()
    }
}
