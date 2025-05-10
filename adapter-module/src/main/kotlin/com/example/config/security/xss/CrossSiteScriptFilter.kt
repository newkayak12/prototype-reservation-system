package com.example.config.security.xss

import com.example.config.security.xss.properties.XssBlacklist
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

class CrossSiteScriptFilter(private val xssBlacklist: XssBlacklist) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain)
    = filterChain.doFilter(RequestWrapper(request), response)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val servletPath = request.servletPath
        return !xssBlacklist.path.contains(servletPath)
    }
}