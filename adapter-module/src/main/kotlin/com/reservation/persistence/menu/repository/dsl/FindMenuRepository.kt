package com.reservation.persistence.menu.repository.dsl

import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.menu.port.output.FindMenu
import com.reservation.menu.port.output.FindMenu.FindMenuPhotoResult
import com.reservation.menu.port.output.FindMenu.FindMenuResult
import com.reservation.persistence.menu.entity.QMenuEntity.menuEntity
import com.reservation.persistence.menu.entity.QMenuPhotoEntity.menuPhotoEntity
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class FindMenuRepository(
    private val query: JPAQueryFactory,
) : FindMenu {

    override fun query(menuId: String): FindMenuResult? {
        val queryResult =  query.select(
            menuEntity.identifier,
            menuEntity.restaurantId,
            menuEntity.title,
            menuEntity.description,
            menuEntity.price,
            menuEntity.isRepresentative,
            menuEntity.isRecommended,
            menuEntity.isVisible,
            menuPhotoEntity.identifier,
            menuPhotoEntity.url,
        )
            .from(menuEntity)
            .where(MenuQuerySpec.menuIdEq(menuId))
            .fetch()
            .filter { it[menuEntity.identifier] != null }


        if(queryResult.isEmpty()) return null

        val photos = transformPhotos(queryResult)
        return transformMenu(queryResult, photos)
    }

    private fun transformPhotos(values: List<Tuple>): List<FindMenuPhotoResult> =
        values
            .filter {
                it[menuPhotoEntity.identifier] != null &&
                    it[menuPhotoEntity.url] != null
            }
            .map {
                FindMenuPhotoResult(
                    it[menuPhotoEntity.identifier] ?: "",
                    it[menuPhotoEntity.url] ?: ""
                )
            }

    private fun transformMenu(
        values: List<Tuple>,
        photos: List<FindMenuPhotoResult>,
    ): FindMenuResult {
        val it = values.first()


        return FindMenuResult(
            identifier = it[menuEntity.identifier] ?: "",
            restaurantId = it[menuEntity.restaurantId] ?: "",
            title = it[menuEntity.title] ?: "",
            description = it[menuEntity.description] ?: "",
            price = it[menuEntity.price] ?: BigDecimal.ZERO,
            isRepresentative = it[menuEntity.isRepresentative] ?: false,
            isRecommended = it[menuEntity.isRecommended] ?: false,
            isVisible = it[menuEntity.isVisible] ?: false,
            photos = photos
        )
    }

}
