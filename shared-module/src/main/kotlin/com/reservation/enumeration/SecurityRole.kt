package com.reservation.enumeration

enum class SecurityRole {
    ROLE_ADMIN,
    ROLE_MANAGER,
    ROLE_USER,
    ;

    companion object {
        fun fromRole(role: Role): SecurityRole {
            return when (role) {
                Role.USER -> ROLE_USER
                Role.RESTAURANT_OWNER -> ROLE_MANAGER
                else -> ROLE_ADMIN
            }
        }

        fun toRole(securityRole: SecurityRole): Role {
            return when (securityRole) {
                ROLE_USER -> Role.USER
                ROLE_MANAGER -> Role.RESTAURANT_OWNER
                else -> Role.ROOT
            }
        }
    }
}
