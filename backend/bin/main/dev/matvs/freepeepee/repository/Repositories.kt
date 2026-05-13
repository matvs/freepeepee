package dev.matvs.freepeepee.repository

import dev.matvs.freepeepee.domain.AppUser
import dev.matvs.freepeepee.domain.AuditEntry
import dev.matvs.freepeepee.domain.Toilet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ToiletRepository : JpaRepository<Toilet, UUID> {

    /**
     * Find toilets within [radiusMeters] of (lon,lat), ordered by distance ascending.
     * Uses geography type so distance is in metres on the WGS84 spheroid.
     */
    @Query(
        value = """
            SELECT *
            FROM toilet
            WHERE ST_DWithin(location, ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography, :radius)
            ORDER BY location <-> ST_SetSRID(ST_MakePoint(:lon,:lat),4326)::geography
        """,
        nativeQuery = true
    )
    fun findNearby(
        @Param("lon") lon: Double,
        @Param("lat") lat: Double,
        @Param("radius") radiusMeters: Double
    ): List<Toilet>

    /** Full text search on name + notes + address. */
    @Query(
        value = """
            SELECT *
            FROM toilet
            WHERE to_tsvector('simple', coalesce(name,'')||' '||coalesce(notes,'')||' '||coalesce(address,''))
                  @@ plainto_tsquery('simple', :q)
        """,
        nativeQuery = true
    )
    fun search(@Param("q") query: String): List<Toilet>
}

@Repository
interface UserRepository : JpaRepository<AppUser, UUID> {
    fun findByUsername(username: String): AppUser?
}

@Repository
interface AuditRepository : JpaRepository<AuditEntry, Long>
