package com.reservation.persistence.menu.repository.adapter

import com.reservation.menu.port.output.CreateMenu
import com.reservation.menu.port.output.CreateMenu.CreateMenuInquiry
import com.reservation.persistence.menu.entity.MenuEntity
import com.reservation.persistence.menu.entity.MenuPhotoEntity
import com.reservation.persistence.menu.repository.jpa.MenuJpaRepository
import org.springframework.stereotype.Component

@Component
class CreateMenuAdapter(
    val jpaRepository: MenuJpaRepository,
) : CreateMenu {
    override fun command(inquiry: CreateMenuInquiry): Boolean {
        val menu =
            jpaRepository.save(
                MenuEntity(
                    restaurantId = inquiry.restaurantId,
                    title = inquiry.title,
                    description = inquiry.description,
                    price = inquiry.price,
                ),
            )

        menu.adjustPhotos {
            addAll(inquiry.photoUrl.map { MenuPhotoEntity(menu, it) })
        }

        return menu.id != null
    }
}
