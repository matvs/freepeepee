package dev.matvs.freepeepee.domain

import jakarta.persistence.*
import java.time.OffsetDateTime
import java.util.UUID

enum class Role { USER, ADMIN }

@Entity
@Table(name = "app_user")
class AppUser(
    @Id @GeneratedValue
    var id: UUID? = null,

    @Column(nullable = false, unique = true, length = 64)
    var username: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var role: Role = Role.USER,

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "is_enabled", nullable = false)
    var isEnabled: Boolean = true
)
