package dev.matvs.freepeepee.web

import dev.matvs.freepeepee.service.AuthResult
import dev.matvs.freepeepee.service.AuthService
import dev.matvs.freepeepee.service.ToiletOpResult
import dev.matvs.freepeepee.service.ToiletService
import dev.matvs.freepeepee.web.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/auth")
class AuthController(private val auth: AuthService) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): ResponseEntity<Any> =
        when (val r = auth.login(req.username, req.password, req.rememberMe)) {
            is AuthResult.Success -> ResponseEntity.ok(
                TokenResponse(r.tokens.access, r.tokens.refresh, r.tokens.expiresAt.epochSecond, r.role)
            )
            AuthResult.InvalidCredentials -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "invalid_credentials"))
            AuthResult.Disabled -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "account_disabled"))
        }
}

@RestController
@RequestMapping("/api/toilets")
class ToiletController(private val service: ToiletService) {

    @GetMapping
    fun list(): List<ToiletDto> = service.list().map(ToiletDto::from)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<ToiletDto> =
        when (val r = service.get(id)) {
            is ToiletOpResult.Ok -> ResponseEntity.ok(ToiletDto.from(r.value))
            ToiletOpResult.NotFound -> ResponseEntity.notFound().build()
            ToiletOpResult.VersionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    @PostMapping
    fun create(@Valid @RequestBody req: ToiletCreateRequest): ResponseEntity<ToiletDto> =
        ResponseEntity.status(HttpStatus.CREATED).body(ToiletDto.from(service.create(req)))

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody req: ToiletUpdateRequest): ResponseEntity<Any> =
        when (val r = service.update(id, req)) {
            is ToiletOpResult.Ok -> ResponseEntity.ok(ToiletDto.from(r.value))
            ToiletOpResult.NotFound -> ResponseEntity.notFound().build()
            ToiletOpResult.VersionConflict -> ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "version_conflict"))
        }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> =
        when (service.delete(id)) {
            is ToiletOpResult.Ok -> ResponseEntity.noContent().build()
            ToiletOpResult.NotFound -> ResponseEntity.notFound().build()
            else -> ResponseEntity.internalServerError().build()
        }

    @GetMapping("/nearby")
    fun nearby(
        @RequestParam lat: Double,
        @RequestParam lon: Double,
        @RequestParam(defaultValue = "1500") radius: Double
    ): List<ToiletDto> = service.nearby(lat, lon, radius).map(ToiletDto::from)

    @GetMapping("/search")
    fun search(@RequestParam q: String): List<ToiletDto> = service.search(q).map(ToiletDto::from)
}
