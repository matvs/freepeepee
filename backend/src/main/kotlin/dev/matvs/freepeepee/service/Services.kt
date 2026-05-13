package dev.matvs.freepeepee.service

import dev.matvs.freepeepee.domain.Toilet
import dev.matvs.freepeepee.repository.ToiletRepository
import dev.matvs.freepeepee.repository.UserRepository
import dev.matvs.freepeepee.security.JwtService
import dev.matvs.freepeepee.security.TokenPair
import dev.matvs.freepeepee.web.dto.ToiletCreateRequest
import dev.matvs.freepeepee.web.dto.ToiletUpdateRequest
import jakarta.persistence.OptimisticLockException
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

sealed interface AuthResult {
    data class Success(val tokens: TokenPair, val role: String) : AuthResult
    data object InvalidCredentials : AuthResult
    data object Disabled : AuthResult
}

@Service
class AuthService(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    private val jwt: JwtService,
    private val audit: AuditService
) {
    fun login(username: String, password: String, rememberMe: Boolean): AuthResult {
        val user = users.findByUsername(username)
        if (user == null || !encoder.matches(password, user.passwordHash)) {
            audit.recordLogin(username, success = false)
            return AuthResult.InvalidCredentials
        }
        if (!user.isEnabled) return AuthResult.Disabled
        audit.recordLogin(username, success = true)
        return AuthResult.Success(jwt.issue(user.username, user.role.name, rememberMe), user.role.name)
    }
}

sealed interface ToiletOpResult<out T> {
    data class Ok<T>(val value: T) : ToiletOpResult<T>
    data object NotFound : ToiletOpResult<Nothing>
    data object VersionConflict : ToiletOpResult<Nothing>
}

@Service
class ToiletService(
    private val repo: ToiletRepository,
    private val audit: AuditService
) {
    private val gf = GeometryFactory(PrecisionModel(), 4326)

    fun list(): List<Toilet> = repo.findAll()

    fun get(id: UUID): ToiletOpResult<Toilet> =
        repo.findById(id).map { ToiletOpResult.Ok(it) as ToiletOpResult<Toilet> }.orElse(ToiletOpResult.NotFound)

    @Transactional
    fun create(req: ToiletCreateRequest): Toilet {
        val saved = repo.save(req.toEntity())
        audit.recordCreate("Toilet", saved.id!!, snapshot(saved))
        return saved
    }

    @Transactional
    fun update(id: UUID, req: ToiletUpdateRequest): ToiletOpResult<Toilet> {
        val existing = repo.findById(id).orElse(null) ?: return ToiletOpResult.NotFound
        if (existing.version != req.version) return ToiletOpResult.VersionConflict

        val before = existing.snapshotForAudit()

        existing.name = req.name
        existing.address = req.address
        existing.location = gf.createPoint(Coordinate(req.lon, req.lat)).also { it.setSRID(4326) }
        existing.pinCode = req.pinCode
        existing.isWorking = req.isWorking
        existing.toiletType = req.toiletType
        existing.notes = req.notes

        return try {
            val saved = repo.save(existing)
            audit.recordUpdate("Toilet", id, before, saved.snapshotForAudit())
            ToiletOpResult.Ok(saved)
        } catch (_: OptimisticLockException) {
            ToiletOpResult.VersionConflict
        }
    }

    @Transactional
    fun delete(id: UUID): ToiletOpResult<Unit> {
        val existing = repo.findById(id).orElse(null) ?: return ToiletOpResult.NotFound
        audit.recordDelete("Toilet", id, snapshot(existing))
        repo.delete(existing)
        return ToiletOpResult.Ok(Unit)
    }

    fun nearby(lat: Double, lon: Double, radiusMeters: Double): List<Toilet> =
        repo.findNearby(lon = lon, lat = lat, radiusMeters = radiusMeters)

    fun search(query: String): List<Toilet> = repo.search(query)

    private fun snapshot(t: Toilet) =
        """{"name":"${t.name}","addr":"${t.address}","lon":${t.location.x},"lat":${t.location.y},"pin":${t.pinCode?.let{"\"$it\""}},"working":${t.isWorking},"type":"${t.toiletType}","notes":${t.notes?.let{"\"$it\""}}}"""
}

/** Mutable snapshot used purely for field-level diffing in AuditService. */
data class ToiletSnapshot(
    val name: String, val address: String, val lat: Double, val lon: Double,
    val pinCode: String?, val isWorking: Boolean, val toiletType: String, val notes: String?
)

private fun Toilet.snapshotForAudit() = ToiletSnapshot(
    name, address, location.y, location.x, pinCode, isWorking, toiletType.name, notes
)
