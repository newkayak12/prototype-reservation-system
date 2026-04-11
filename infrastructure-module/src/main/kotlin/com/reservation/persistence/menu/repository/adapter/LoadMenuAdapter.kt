package com.reservation.persistence.menu.repository.adapter

import com.reservation.menu.port.output.LoadMenuById
import com.reservation.menu.port.output.LoadMenuById.LoadMenu
import com.reservation.menu.port.output.LoadMenuById.LoadMenu.LoadMenuPhoto
import com.reservation.persistence.menu.repository.jpa.MenuJpaRepository
import org.springframework.stereotype.Component

@Component
class LoadMenuAdapter(
    val jpaRepository: MenuJpaRepository,
) : LoadMenuById {
    override fun loadById(id: String): LoadMenu? {
        return jpaRepository.findByIdEquals(id)
            ?.let {
                LoadMenu(
                    it.id!!,
                    it.restaurantId,
                    it.title,
                    it.description,
                    it.photos.map { photo -> LoadMenuPhoto(photo.url) },
                    it.isRepresentative,
                    it.isRecommended,
                    it.isVisible,
                    it.price,
                )
            }
            ?: return null
    }
}
