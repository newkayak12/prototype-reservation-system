package com.reservation.config.security.jwt

import com.reservation.config.security.jwt.properties.JwtWhitelist
import com.reservation.utilities.provider.JWTProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtFilter(
    private val jwtProvider: JWTProvider,
    private val jwtPath: JwtWhitelist?,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val bearerToken: String = request.getHeader(HttpHeaders.AUTHORIZATION)

        if (jwtProvider.validate(bearerToken)) {
            val record = jwtProvider.decrypt(bearerToken)
            val principal =
                TokenableAuthenticationDetails(
                    record.id,
                    record.username,
                    "",
                    record.securityRole,
                )
            val authentication: Authentication =
                UsernamePasswordAuthenticationToken(
                    principal,
                    bearerToken,
                    principal.authorities,
                )
            SecurityContextHolder.getContext().authentication = authentication
        }

        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val servletPath = request.servletPath
        return jwtPath?.path?.contains(servletPath) ?: false
    }
}
