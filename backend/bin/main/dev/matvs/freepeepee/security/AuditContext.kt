package dev.matvs.freepeepee.security

import dev.matvs.freepeepee.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

/**
 * Pulls actor, IP, user-agent and a per-request id from the current servlet request.
 * Falls back to "system" when run outside an HTTP context (e.g. tests).
 */
@Component
class AuditContext(private val users: UserRepository) {

    fun actorName(): String = SecurityContextHolder.getContext().authentication?.name ?: "anonymous"

    fun actorId(): UUID? = users.findByUsername(actorName())?.id

    fun ip(): String? = req()?.let { extractIp(it) }

    fun userAgent(): String? = req()?.getHeader("User-Agent")?.take(512)

    /** A request id, looked up from X-Request-Id, else generated and cached on the request. */
    fun requestId(): UUID = req()?.let { r ->
        val existing = r.getAttribute(ATTR) as? UUID
        if (existing != null) return existing
        val id = r.getHeader("X-Request-Id")?.let(::safeUuid) ?: UUID.randomUUID()
        r.setAttribute(ATTR, id)
        id
    } ?: UUID.randomUUID()

    private fun req(): HttpServletRequest? =
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request

    private fun extractIp(r: HttpServletRequest): String {
        val xff = r.getHeader("X-Forwarded-For")
        return if (!xff.isNullOrBlank()) xff.substringBefore(",").trim() else r.remoteAddr
    }

    private fun safeUuid(s: String) = try { UUID.fromString(s) } catch (_: Exception) { null }

    private companion object { const val ATTR = "freepeepee.requestId" }
}
