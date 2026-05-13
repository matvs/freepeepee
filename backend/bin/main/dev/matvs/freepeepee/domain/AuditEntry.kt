package dev.matvs.freepeepee.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime
import java.util.UUID

enum class AuditOperation { CREATE, UPDATE, DELETE, LOGIN, LOGIN_FAIL }

@Entity
@Table(name = "audit_entry")
class AuditEntry(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "occurred_at", nullable = false)
    var occurredAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "actor_id")
    var actorId: UUID? = null,

    @Column(name = "actor_name", nullable = false, length = 64)
    var actorName: String,

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "actor_ip", columnDefinition = "inet")
    var actorIp: String? = null,

    @Column(name = "actor_agent", length = 512)
    var actorAgent: String? = null,

    @Column(name = "entity_type", nullable = false, length = 64)
    var entityType: String,

    @Column(name = "entity_id")
    var entityId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var operation: AuditOperation,

    @Column(name = "field_name", length = 64)
    var fieldName: String? = null,

    @Column(name = "old_value", columnDefinition = "text")
    var oldValue: String? = null,

    @Column(name = "new_value", columnDefinition = "text")
    var newValue: String? = null,

    @Column(name = "request_id")
    var requestId: UUID? = null
)
