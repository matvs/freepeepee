package dev.matvs.freepeepee.web.dto

import dev.matvs.freepeepee.domain.Toilet
import dev.matvs.freepeepee.domain.ToiletType
import jakarta.validation.constraints.*
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import java.time.OffsetDateTime
import java.util.UUID

private val GF = GeometryFactory(PrecisionModel(), 4326)

data class ToiletDto(
    val id: UUID?,
    val name: String,
    val address: String,
    val lat: Double,
    val lon: Double,
    val pinCode: String?,
    val isWorking: Boolean,
    val toiletType: ToiletType,
    val notes: String?,
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?,
    val version: Long
) {
    companion object {
        fun from(t: Toilet) = ToiletDto(
            id = t.id, name = t.name, address = t.address,
            lat = t.location.y, lon = t.location.x,
            pinCode = t.pinCode, isWorking = t.isWorking,
            toiletType = t.toiletType, notes = t.notes,
            createdAt = t.createdAt, updatedAt = t.updatedAt, version = t.version
        )
    }
}

data class ToiletCreateRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotBlank @field:Size(max = 255) val address: String,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val lat: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val lon: Double,
    @field:Size(max = 32) val pinCode: String? = null,
    val isWorking: Boolean = true,
    val toiletType: ToiletType = ToiletType.OTHER,
    @field:Size(max = 4000) val notes: String? = null
) {
    fun toEntity() = Toilet(
        name = name, address = address,
        location = GF.createPoint(Coordinate(lon, lat)).also { it.setSRID(4326) },
        pinCode = pinCode, isWorking = isWorking,
        toiletType = toiletType, notes = notes
    )
}

data class ToiletUpdateRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotBlank @field:Size(max = 255) val address: String,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val lat: Double,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val lon: Double,
    @field:Size(max = 32) val pinCode: String?,
    val isWorking: Boolean,
    val toiletType: ToiletType,
    @field:Size(max = 4000) val notes: String?,
    @field:NotNull val version: Long
)

data class LoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
    val rememberMe: Boolean = false
)

data class TokenResponse(val accessToken: String, val refreshToken: String?, val expiresAtEpoch: Long)
