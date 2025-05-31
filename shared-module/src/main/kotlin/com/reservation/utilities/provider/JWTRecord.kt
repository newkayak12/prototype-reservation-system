package com.reservation.utilities.provider

import com.reservation.enumeration.Role
import com.reservation.enumeration.SecurityRole

data class JWTRecord(
    val id: String,
    val username: String,
) : Tokenable {
    lateinit var role: Role
    lateinit var securityRole: SecurityRole

    constructor(id: String, username: String, role: Role) : this(id, username) {
        this.role = role
        this.securityRole = SecurityRole.fromRole(role)
    }

    constructor(id: String, username: String, securityRole: SecurityRole) : this(id, username) {
        this.securityRole = securityRole
        this.role = SecurityRole.toRole(securityRole)
    }

    override fun identity(): String = id

    override fun role(): String = securityRole.name

    override fun username(): String = username
}
