package com.reservation.persistence.user.repository.jpa

import com.reservation.user.User
import org.springframework.data.repository.CrudRepository

interface UserRepository : CrudRepository<User, String>
