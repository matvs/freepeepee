package dev.matvs.freepeepee.service

import dev.matvs.freepeepee.domain.AuditEntry
import dev.matvs.freepeepee.domain.AuditOperation
import dev.matvs.freepeepee.repository.AuditRepository
import dev.matvs.freepeepee.security.AuditContext
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * Records audit entries. Append-only by DB trigger; this service never updates or deletes.
 */
@Service
class AuditService(private val repo: AuditRepository, private val ctx: AuditContext) {

    fun recordLogin(username: String, success: Boolean) = persist(
        AuditEntry(
            actorName = username,
            actorIp = ctx.ip(),
            actorAgent = ctx.userAgent(),
            entityType = "AppUser",
            operation = if (success) AuditOperation.LOGIN else AuditOperation.LOGIN_FAIL,
            requestId = ctx.requestId()
        )
    )

    fun recordCreate(entityType: String, entityId: UUID, snapshot: String) = persist(base(entityType, entityId, AuditOperation.CREATE).apply { newValue = snapshot })

    fun recordDelete(entityType: String, entityId: UUID, snapshot: String) = persist(base(entityType, entityId, AuditOperation.DELETE).apply { oldValue = snapshot })

    /**
     * Records one row per CHANGED field. Unchanged fields produce nothing.
     */
    fun <T : Any> recordUpdate(entityType: String, entityId: UUID, before: T, after: T) {
        val reqId = ctx.requestId()
        diff(before, after).forEach { (field, old, new) ->
            persist(base(entityType, entityId, AuditOperation.UPDATE).apply {
                fieldName = field
                oldValue = old?.toString()
                newValue = new?.toString()
                requestId = reqId
            })
        }
    }

    private fun base(entityType: String, entityId: UUID, op: AuditOperation) = AuditEntry(
        actorId = ctx.actorId(),
        actorName = ctx.actorName(),
        actorIp = ctx.ip(),
        actorAgent = ctx.userAgent(),
        entityType = entityType,
        entityId = entityId,
        operation = op,
        requestId = ctx.requestId()
    )

    private fun persist(e: AuditEntry) { repo.save(e) }

    /** Returns triples (fieldName, oldValue, newValue) for fields whose value differs. */
    private fun <T : Any> diff(a: T, b: T): List<Triple<String, Any?, Any?>> {
        require(a::class == b::class)
        @Suppress("UNCHECKED_CAST")
        val props = a::class.memberProperties as Collection<KProperty1<T, *>>
        return props
            .filter { it.name !in IGNORED_FIELDS }
            .mapNotNull { p ->
                val av = p.get(a); val bv = p.get(b)
                if (av != bv) Triple(p.name, av, bv) else null
            }
    }

    companion object {
        private val IGNORED_FIELDS = setOf("createdAt", "updatedAt", "version", "id")
    }
}
