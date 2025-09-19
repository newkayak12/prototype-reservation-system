package com.reservation.persistence.menu.repository.adapter

import com.reservation.common.exceptions.NoSuchPersistedElementException
import com.reservation.menu.port.output.UpdateMenu
import com.reservation.menu.port.output.UpdateMenu.UpdateMenuInquiry
import com.reservation.persistence.menu.entity.MenuEntity
import com.reservation.persistence.menu.repository.jpa.MenuJpaRepository
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class UpdateMenuAdapter(
    val jpaRepository: MenuJpaRepository,
) : UpdateMenu {
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
}
