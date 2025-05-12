package com.example.config.security.jwt

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class TokenableAuthenticationDetails(
    private val id: String,
    private val username: String,
    private val password: String,
    private val securityRole: SecurityRole,
) : UserDetails, Tokenable {
    override fun identity(): String = id

    override fun role(): String = securityRole.name

    override fun getAuthorities(): Collection<GrantedAuthority> = setOf(securityRole)

    override fun getPassword(): String = password

    override fun getUsername(): String = username
}
