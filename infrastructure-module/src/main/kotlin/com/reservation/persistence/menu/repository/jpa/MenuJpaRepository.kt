package com.reservation.persistence.menu.repository.jpa

import com.reservation.persistence.menu.entity.MenuEntity
import org.springframework.data.repository.CrudRepository

interface MenuJpaRepository : CrudRepository<MenuEntity, String> {
    fun save(entity: MenuEntity): MenuEntity

    fun findByIdEquals(id: String): MenuEntity?
}
