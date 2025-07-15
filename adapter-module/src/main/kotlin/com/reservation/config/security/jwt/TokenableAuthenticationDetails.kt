package com.reservation.config.security.jwt

import com.reservation.enumeration.SecurityRole
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class TokenableAuthenticationDetails(
    private val id: String,
    private val username: String,
    private val password: String,
    private val securityRole: SecurityRole,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> =
        setOf(SimpleGrantedAuthority(securityRole.name))

    fun identifier(): String = id

    override fun getPassword(): String = password

    override fun getUsername(): String = username
}
