package com.example.config.security.jwt

import com.example.config.security.jwt.properties.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import javax.crypto.SecretKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

class JwtProvider(properties: JwtProperties) : TokenProvider<TokenableAuthenticationDetails> {
    private val prefix: String = "Bearer"
    private val expireTime: Long = properties.expireTime
    private val signingKey: SecretKey =
        Keys.hmacShaKeyFor(
            Base64.getEncoder().encode(properties.secret.toByteArray(Charsets.UTF_8)),
        )
    private val issuer: String = properties.issuer
    private val id: String = "id"
    private val role: String = "role"

    private fun removeBearer(bearerToken: String): String =
        bearerToken.replace(prefix, "").trim { it <= ' ' }

    private fun parse(bearerToken: String): Claims {
        val token = removeBearer(bearerToken)
        val parser: JwtParser = Jwts.parser().verifyWith(signingKey).build()
        val jws: Jws<Claims> = parser.parseSignedClaims(token)

        return jws.payload
    }

    override fun tokenize(tokenable: TokenableAuthenticationDetails): String {
        val now = LocalDateTime.now()
        return prefix +
            Jwts.builder()
                .id(tokenable.identity())
                .issuer(issuer)
                .issuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(
                    Date.from(
                        now.plus(expireTime, ChronoUnit.MILLIS).atZone(ZoneId.systemDefault())
                            .toInstant(),
                    ),
                )
                .claims(
                    mapOf(
                        Pair(id, tokenable.username),
                        Pair(role, tokenable.role()),
                    ),
                )
                .signWith(signingKey)
                .compact()
    }

    override fun validate(token: String): Boolean {
        val claims: Claims = parse(token)
        val expireDate: Date = claims.expiration
        val now = LocalDateTime.now()
        val expireLocalDateTime = LocalDateTime.from(expireDate.toInstant())

        return now.isBefore(expireLocalDateTime)
    }

    override fun decrypt(token: String): TokenableAuthenticationDetails {
        val claims: Claims = parse(token)

        return TokenableAuthenticationDetails(
            claims.id,
            claims[id] as String,
            "",
            claims[role] as SecurityRole,
        )
    }
}
