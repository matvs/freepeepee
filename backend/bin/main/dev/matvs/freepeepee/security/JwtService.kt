package dev.matvs.freepeepee.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.SecretKey

data class TokenPair(val access: String, val refresh: String?, val expiresAt: Instant)

@Service
class JwtService(
    @Value("\${freepeepee.jwt.secret}") private val secret: String,
    @Value("\${freepeepee.jwt.issuer}") private val issuer: String,
    @Value("\${freepeepee.jwt.access-ttl-minutes}") private val accessTtlMin: Long,
    @Value("\${freepeepee.jwt.refresh-ttl-days}") private val refreshTtlDays: Long
) {
    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    fun issue(username: String, role: String, rememberMe: Boolean): TokenPair {
        val now = Instant.now()
        val exp = now.plus(accessTtlMin, ChronoUnit.MINUTES)
        val access = Jwts.builder()
            .issuer(issuer).subject(username).claim("role", role)
            .issuedAt(java.util.Date.from(now)).expiration(java.util.Date.from(exp))
            .signWith(key).compact()
        val refresh = if (rememberMe) {
            val rExp = now.plus(refreshTtlDays, ChronoUnit.DAYS)
            Jwts.builder()
                .issuer(issuer).subject(username).claim("typ", "refresh")
                .issuedAt(java.util.Date.from(now)).expiration(java.util.Date.from(rExp))
                .signWith(key).compact()
        } else null
        return TokenPair(access, refresh, exp)
    }

    /** Returns subject (username) on success, null on any failure. */
    fun verify(token: String): String? = runCatching {
        Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
            .parseSignedClaims(token).payload.subject
    }.getOrNull()

    fun roleOf(token: String): String? = runCatching {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload.get("role", String::class.java)
    }.getOrNull()
}
