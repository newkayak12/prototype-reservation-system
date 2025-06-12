package com.reservation.persistence.user.repository.jpa

import com.reservation.persistence.user.entity.UserEntity
import org.springframework.data.repository.CrudRepository
import java.util.Optional

interface UserJpaRepository : CrudRepository<UserEntity, String> {
    fun findUserEntityByLoginIdEqualsAndEmailEquals(
        loginId: String,
        email: String,
    ): Optional<UserEntity>
}
