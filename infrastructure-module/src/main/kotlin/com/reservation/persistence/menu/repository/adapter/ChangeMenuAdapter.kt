package com.reservation.persistence.menu.repository.adapter

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.menu.port.output.ChangeMenu
import com.reservation.menu.port.output.ChangeMenu.UpdateMenuInquiry
import com.reservation.persistence.menu.entity.MenuEntity
import com.reservation.persistence.menu.entity.MenuPhotoEntity
import com.reservation.persistence.menu.repository.jpa.MenuJpaRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class ChangeMenuAdapter(
    val jpaRepository: MenuJpaRepository,
) : ChangeMenu {
    override fun command(inquiry: UpdateMenuInquiry): Boolean {
        val entity: MenuEntity =
            jpaRepository.findByIdEquals(inquiry.id)
                ?: throw NoSuchPersistedElementException()

        entity.updateInformation(
            inquiry.title,
            inquiry.description,
            inquiry.price,
        )

        entity.updateFlags(
            inquiry.isRepresentative,
            inquiry.isRecommended,
            inquiry.isVisible,
        )

        entity.updatePhotos(inquiry.photoUrl)

        return true
    }

    private fun MenuEntity.updateInformation(
        title: String,
        description: String,
        price: BigDecimal,
    ) {
        updateTitle(title)
        updateDescription(description)
        updatePrice(price)
    }

    private fun MenuEntity.updateFlags(
        isRepresentative: Boolean,
        isRecommended: Boolean,
        isVisible: Boolean,
    ) {
        if (this.isRepresentative != isRepresentative) {
            toggleRepresentative()
        }
        if (this.isRecommended != isRecommended) {
            toggleRecommended()
        }
        if (this.isVisible != isVisible) {
            toggleVisible()
        }
    }

    private fun MenuEntity.updatePhotos(latest: List<String>) {
        val insertTarget = extractInsertTargets(latest)
        val removeTarget = extractRemoveTargets(latest)

        photos.removeAll(removeTarget)
        photos.addAll(insertTarget)
    }

    private fun MenuEntity.extractInsertTargets(latest: List<String>): List<MenuPhotoEntity> {
        val exists: List<String> = photos.map { it.url }

        return latest
            .filterNot { exists.contains(it) }
            .map { MenuPhotoEntity(this, it) }
    }

    private fun MenuEntity.extractRemoveTargets(latest: List<String>): List<MenuPhotoEntity> =
        photos.filterNot { latest.contains(it.url) }
}
