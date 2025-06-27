package com.reservation.utilities.provider

import com.reservation.enumeration.JWTType
import com.reservation.enumeration.JWTVersion
import com.reservation.enumeration.SecurityRole
import com.reservation.exceptions.AlreadyExpiredException
import com.reservation.exceptions.InvalidTokenException
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.IncorrectClaimException
import io.jsonwebtoken.Jws
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.MissingClaimException
import io.jsonwebtoken.PrematureJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SecurityException
import javax.crypto.SecretKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

class JWTProvider(
    secret: String,
    private val duration: Long,
    private val issuer: String,
    private val version: JWTVersion,
) : TokenProvider<JWTRecord> {
    companion object {
        private const val PREFIX = "Bearer"
        private const val ID = "id"
        private const val ROLE = "role"
        private const val VERSION = "version"
        private const val TYPE = "type"
    }

    private val signingKey: SecretKey =
        Keys.hmacShaKeyFor(
            Base64.getEncoder().encode(secret.toByteArray(Charsets.UTF_8)),
        )

    private fun removeBearer(bearerToken: String): String =
        bearerToken.replace(PREFIX, "").trim { it <= ' ' }

    private fun parse(bearerToken: String): Claims {
        val token = removeBearer(bearerToken)
        val parser: JwtParser = Jwts.parser().verifyWith(signingKey).build()
        val jws: Jws<Claims> = parser.parseSignedClaims(token)

        return jws.payload
    }

    override fun tokenize(
        tokenable: JWTRecord,
        type: JWTType,
    ): String {
        val now = LocalDateTime.now()
        return PREFIX +
            Jwts.builder()
                .id(tokenable.identity())
                .issuer(issuer)
                .issuedAt(Date.from(now.atZone(ZoneId.systemDefault()).toInstant()))
                .expiration(
                    Date.from(
                        now.plus(duration, ChronoUnit.MILLIS).atZone(ZoneId.systemDefault())
                            .toInstant(),
                    ),
                )
                .claims(
                    mapOf(
                        Pair(ID, tokenable.username()),
                        Pair(ROLE, tokenable.securityRole.name),
                        Pair(VERSION, version.name),
                        Pair(TYPE, type.title),
                    ),
                )
                .signWith(signingKey)
                .compact()
    }

    override fun validate(
        token: String,
        type: JWTType,
    ): Boolean {
        try {
            val claims: Claims = parse(token)
            val expireDate: Date = claims.expiration
            val now = LocalDateTime.now()
            val expireLocalDateTime =
                LocalDateTime.ofInstant(
                    expireDate.toInstant(),
                    ZoneId.systemDefault(),
                )
            val jwtVersion = claims.get(VERSION, String::class.java)
            val jwtType = claims.get(TYPE, String::class.java)

            return now.isBefore(expireLocalDateTime) &&
                type.title == jwtType &&
                version.name == jwtVersion
        } catch (
            e: SecurityException,
        ) {
            when (e) {
                is ExpiredJwtException -> throw AlreadyExpiredException()
                is PrematureJwtException,
                is MalformedJwtException,
                is UnsupportedJwtException,
                is MissingClaimException,
                is IncorrectClaimException,
                -> throw InvalidTokenException()
            }
        }

        return false
    }

    override fun decrypt(
        token: String,
        type: JWTType,
    ): JWTRecord {
        val claims: Claims = parse(token)

        return JWTRecord(
            claims.id,
            claims[ID] as String,
            SecurityRole.valueOf(claims[ROLE] as String),
        )
    }

    override fun duration(): Long = duration
}
