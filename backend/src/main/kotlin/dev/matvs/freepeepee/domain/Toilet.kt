package dev.matvs.freepeepee.domain

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.locationtech.jts.geom.Point
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.OffsetDateTime
import java.util.UUID

enum class ToiletType { MCDONALDS, GAS_STATION, PARK, CAFE, PUBLIC, OTHER }

@Entity
@Table(name = "toilet")
@EntityListeners(AuditingEntityListener::class)
class Toilet(
    @Id @GeneratedValue
    var id: UUID? = null,

    @Column(nullable = false, length = 120)
    var name: String,

    @Column(nullable = false, length = 255)
    var address: String,

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    var location: Point,

    @Column(name = "pin_code", length = 32)
    var pinCode: String? = null,

    @Column(name = "is_working", nullable = false)
    var isWorking: Boolean = true,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "toilet_type", nullable = false, columnDefinition = "toilet_type")
    var toiletType: ToiletType = ToiletType.OTHER,

    @Column(columnDefinition = "text")
    var notes: String? = null,

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    var createdAt: OffsetDateTime? = null,

    @LastModifiedDate
    @Column(name = "updated_at")
    var updatedAt: OffsetDateTime? = null,

    @Column(name = "created_by")
    var createdBy: UUID? = null,

    @Version
    var version: Long = 0
)
