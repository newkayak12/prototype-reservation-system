package com.reservation.config.security.jwt

import org.springframework.security.core.GrantedAuthority

enum class SecurityRole : GrantedAuthority {
    ROLE_ADMIN,
    ROLE_MANAGER,
    ROLE_USER,
    ;

    override fun getAuthority(): String = this.name
}
