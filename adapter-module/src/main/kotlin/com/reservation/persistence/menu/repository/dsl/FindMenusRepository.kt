package com.reservation.persistence.menu.repository.dsl

import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import com.reservation.menu.port.output.FindMenus
import com.reservation.menu.port.output.FindMenus.FindMenusPhotoResult
import com.reservation.menu.port.output.FindMenus.FindMenusResult
import com.reservation.persistence.menu.entity.QMenuEntity.menuEntity
import com.reservation.persistence.menu.entity.QMenuPhotoEntity.menuPhotoEntity
import org.springframework.stereotype.Component

@Component
class FindMenusRepository(
    val query: JPAQueryFactory,
) : FindMenus {
    private fun fetchPhotosAndGrouping(
        identifiers: Set<String>,
    ): Map<String, List<FindMenusPhotoResult>> =
        query.select(
            menuPhotoEntity.identifier,
            menuPhotoEntity.menu.identifier,
            menuPhotoEntity.url,
        )
            .from(menuPhotoEntity)
            .where(
                MenuPhotoQuerySpec.restaurantIdEq(identifiers),
            )
            .fetch()
            .filter {
                it[menuPhotoEntity.menu.identifier] != null &&
                    it[menuPhotoEntity.identifier] != null &&
                    it[menuPhotoEntity.url] != null
            }
            .groupBy { it[menuPhotoEntity.menu.identifier] ?: "" }
            .mapValues { entry ->
                entry.value
                    .map { entity ->
                        FindMenusPhotoResult(
                            entity[menuPhotoEntity.identifier] ?: "",
                            entity[menuPhotoEntity.url] ?: "",
                        )
                    }
            }

    private fun fetchList(restaurantId: String): List<FindMenusResult> =
        query.select(
            Projections.constructor(
                FindMenusResult::class.java,
                menuEntity.identifier,
                menuEntity.restaurantId,
                menuEntity.title,
                menuEntity.description,
                menuEntity.price,
                menuEntity.isRepresentative,
                menuEntity.isRecommended,
                menuEntity.isVisible,
            ),
        )
            .from(menuEntity)
            .where(
                MenuQuerySpec.restaurantIdEq(restaurantId),
            )
            .fetch()

    override fun query(restaurantId: String): List<FindMenusResult> {
        val list = fetchList(restaurantId)
        val identifiers = list.map { it.identifier }.toSet()
        val photos = fetchPhotosAndGrouping(identifiers)

        return list.map {
            val photos = photos[it.identifier] ?: listOf()
            it.bind(photos)
            return@map it
        }
    }
}
