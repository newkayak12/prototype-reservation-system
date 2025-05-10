package com.example.config.security.jwt

import com.example.config.security.jwt.properties.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jws
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import javax.crypto.SecretKey

class JwtProvider(properties: JwtProperties) : TokenProvider<TokenableAuthenticationDetails> {
    private val PREFIX: String = "Bearer"
    private val EXPIRE_TIME: Long = properties.expireTime
    private val SIGNING_KEY: SecretKey = Keys.hmacShaKeyFor(Base64.getEncoder().encode(properties.secret.toByteArray(Charsets.UTF_8)))
    private val ISSUER: String = properties.issuer
    private val ID: String = "id"
    private val ROLE: String = "role"

    private fun removeBearer(bearerToken: String): String = bearerToken.replace(PREFIX, "").trim { it <= ' ' }
    private fun parse(bearerToken: String): Claims {
        val token = removeBearer(bearerToken)
        val parser: JwtParser = Jwts.parser().verifyWith(SIGNING_KEY).build()
        val jws: Jws<Claims> = parser.parseSignedClaims(token)

        return jws.payload
    }




    override fun tokenize(tokenable: TokenableAuthenticationDetails): String {
        val now = LocalDateTime.now()
        return PREFIX +  Jwts.builder()
            .id(tokenable.identity())
            .issuer(ISSUER)
            .issuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
            .expiration(Date.from(now.plus(EXPIRE_TIME, ChronoUnit.MILLIS).atZone(ZoneId.systemDefault()).toInstant()))
            .claims(
                mapOf(
                    Pair(ID, tokenable.username),
                    Pair(ROLE, tokenable.role())
                )
            )
            .signWith(SIGNING_KEY)
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
            claims.id, claims[ID] as String, "", claims[ROLE] as SecurityRole
        );
    }
}