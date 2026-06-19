# Public Read-Only + Separate /admin — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the toilet catalogue publicly readable without login, while restricting all editing to an env-defined admin reached only via the `/admin` URL, and give admins a change-log viewer.

**Architecture:** Backend opens `GET /api/toilets/**` to everyone and locks writes + `GET /api/audit/**` to `ROLE_ADMIN`; the admin account is provisioned at startup from `ADMIN_USERNAME`/`ADMIN_PASSWORD`, and a new Flyway migration deletes the seeded `matvs` user while preserving toilet/audit data via `ON DELETE SET NULL`. The Angular SPA drops its global auth guard (shell is public), moves login to `/admin`, gates edit controls on an `isAdmin` signal, and adds a `/log` audit viewer.

**Tech Stack:** Spring Boot 3.3 / Kotlin 1.9, Spring Security, Hibernate Spatial, Flyway 10, JJWT, PostgreSQL 16 + PostGIS; Angular 18 (standalone + signals), Angular Material, Leaflet; Kotest + Testcontainers, Karma + Jasmine.

## Global Constraints

- **No secret in the repo.** `ADMIN_USERNAME` / `ADMIN_PASSWORD` live only in `.env` (gitignored). `.env.example` holds placeholders only.
- **Do not edit applied migrations** (`V1`–`V4`). Flyway checksums are frozen on production. New changes go in `V5`.
- **Preserve production data.** The `toilet` rows must survive the upgrade.
- **Backend coverage gate:** Jacoco minimum 0.75 must stay green (`jacocoTestCoverageVerification`).
- **Branch:** all work on `feature/public-readonly-admin`; single PR to `main`.
- **Reads need no token; writes need an admin token.** Anonymous write → 401 (no/invalid token) or 403 (token lacks ADMIN).
- **Admin is reached only via `/admin`** — no link/button anywhere in the public UI.
- Commit messages end with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.

**Build/test commands (used throughout):**
- Backend, one test class: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.<ClassName>"` (Testcontainers needs Docker running).
- Backend, full gate: `cd backend && ./gradlew build jacocoTestReport jacocoTestCoverageVerification`.
- Frontend, unit tests: `cd frontend && npm test` (needs headless Chrome; if Chrome is unavailable in the dev box, the frontend CI workflow runs them — additionally always run `npm run build` locally to catch template/type errors).
- Frontend, type/template check: `cd frontend && npm run build`.

---

## File structure

**Backend**
- Create `backend/src/main/kotlin/dev/matvs/freepeepee/config/AdminUserInitializer.kt` — startup bootstrap of the admin user.
- Create `backend/src/main/resources/db/migration/V5__public_readonly_admin.sql` — FK relax + delete `matvs`.
- Create `backend/src/main/kotlin/dev/matvs/freepeepee/web/AuditController.kt` — admin-only audit list endpoint. *(kept separate from `Controllers.kt` because it's a new admin surface with its own authorization boundary)*
- Modify `config/SecurityConfig.kt` — public reads, admin writes, admin audit.
- Modify `repository/Repositories.kt` — paged audit query.
- Modify `web/dto/Dtos.kt` — `AuditDto`, `TokenResponse.role`.
- Modify `web/Controllers.kt` — `AuthController` passes role through.
- Modify `resources/application.yml` — `freepeepee.admin.*` keys.
- Modify tests: `PostgisInitializer.kt` (admin test creds), `AuthFlowIT.kt`, `ToiletApiIT.kt`; create `AdminBootstrapIT.kt`, `MigrationV5IT.kt`, `AuditApiIT.kt`.

**Frontend**
- Modify `core/auth/auth.service.ts` (+ `auth.service.spec.ts`) — role + `isAdmin`, parameterized logout.
- Create `core/guards/admin.guard.ts` (+ spec); delete `core/guards/auth.guard.ts`; modify `core/guards/guest.guard.ts`.
- Modify `core/http/error.interceptor.ts` — 401/403 → `/admin`.
- Modify `app.routes.ts` — public shell, `/admin` login, `/log` audit.
- Modify `features/login/login.component.ts` — post-login nav to `/map`.
- Modify `features/shell/shell.component.{ts,pug}` — admin-only nav/sign-out.
- Modify `features/toilets-list/toilets-list.component.{ts,pug,scss}` — gate edit controls, empty-state copy, mobile polish.
- Modify `features/toilets-map/toilets-map.component.{ts,pug}` — gate add button.
- Create `core/services/audit.service.ts`.
- Create `features/audit-log/audit-log.component.{ts,pug,scss}` (+ spec).

**Config / docs**
- Modify `.env.example`, `docker-compose.yml`, `README.md`, `docs/runbook.md`.

---

## Task 1: Admin bootstrap from env (backend)

**Files:**
- Create: `backend/src/main/kotlin/dev/matvs/freepeepee/config/AdminUserInitializer.kt`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/test/kotlin/dev/matvs/freepeepee/PostgisInitializer.kt`
- Test: `backend/src/test/kotlin/dev/matvs/freepeepee/AdminBootstrapIT.kt`

**Interfaces:**
- Consumes: `UserRepository.findByUsername(String): AppUser?`, `PasswordEncoder`, `AppUser(username, passwordHash, role, isEnabled)`, `Role.ADMIN`.
- Produces: an `AppUser` row with the configured username, `role = ADMIN`, hashed password — every other IT logs in with these test creds (`admin` / `test-admin-pw`).

- [ ] **Step 1: Add admin config keys to `application.yml`**

Under the existing `freepeepee:` block (sibling of `jwt:` / `cors:`), add:

```yaml
  admin:
    username: ${ADMIN_USERNAME:}
    password: ${ADMIN_PASSWORD:}
```

- [ ] **Step 2: Provide admin test creds in `PostgisInitializer`**

Add two lines inside the existing `TestPropertyValues.of(...)` call (after the cors line):

```kotlin
            "freepeepee.cors.allowed-origins=http://localhost:4200",
            "freepeepee.admin.username=admin",
            "freepeepee.admin.password=test-admin-pw"
```

- [ ] **Step 3: Write the failing test**

Create `backend/src/test/kotlin/dev/matvs/freepeepee/AdminBootstrapIT.kt`:

```kotlin
package dev.matvs.freepeepee

import dev.matvs.freepeepee.domain.Role
import dev.matvs.freepeepee.repository.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [PostgisInitializer::class])
class AdminBootstrapIT(
    @Autowired private val users: UserRepository
) : DescribeSpec({
    describe("AdminUserInitializer") {
        it("provisions the env-configured admin as ADMIN and enabled") {
            val admin = users.findByUsername("admin")
            admin.shouldNotBeNull()
            admin.role shouldBe Role.ADMIN
            admin.isEnabled shouldBe true
        }
    }
})
```

- [ ] **Step 4: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.AdminBootstrapIT"`
Expected: FAIL — `findByUsername("admin")` returns null (no initializer yet).

- [ ] **Step 5: Implement `AdminUserInitializer`**

Create `backend/src/main/kotlin/dev/matvs/freepeepee/config/AdminUserInitializer.kt`:

```kotlin
package dev.matvs.freepeepee.config

import dev.matvs.freepeepee.domain.AppUser
import dev.matvs.freepeepee.domain.Role
import dev.matvs.freepeepee.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Provisions the single admin account from ADMIN_USERNAME / ADMIN_PASSWORD on every boot.
 * Upserts so password rotation = change .env + restart. Fails fast if creds are missing.
 */
@Component
class AdminUserInitializer(
    private val users: UserRepository,
    private val encoder: PasswordEncoder,
    @Value("\${freepeepee.admin.username:}") private val username: String,
    @Value("\${freepeepee.admin.password:}") private val password: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        require(username.isNotBlank() && password.isNotBlank()) {
            "ADMIN_USERNAME and ADMIN_PASSWORD must both be set; refusing to start without an admin account."
        }
        val hash = encoder.encode(password)
        val existing = users.findByUsername(username)
        if (existing == null) {
            users.save(AppUser(username = username, passwordHash = hash, role = Role.ADMIN, isEnabled = true))
            log.info("Created admin user '{}'", username)
        } else {
            existing.passwordHash = hash
            existing.role = Role.ADMIN
            existing.isEnabled = true
            users.save(existing)
            log.info("Updated admin user '{}'", username)
        }
    }
}
```

- [ ] **Step 6: Run it to confirm it passes**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.AdminBootstrapIT"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/dev/matvs/freepeepee/config/AdminUserInitializer.kt \
        backend/src/main/resources/application.yml \
        backend/src/test/kotlin/dev/matvs/freepeepee/PostgisInitializer.kt \
        backend/src/test/kotlin/dev/matvs/freepeepee/AdminBootstrapIT.kt
git commit -m "feat(backend): provision admin user from env at startup

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Migration V5 — delete `matvs`, preserve data (backend)

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__public_readonly_admin.sql`
- Modify: `backend/src/test/kotlin/dev/matvs/freepeepee/AuthFlowIT.kt`
- Modify: `backend/src/test/kotlin/dev/matvs/freepeepee/ToiletApiIT.kt` (login helper only)
- Test: `backend/src/test/kotlin/dev/matvs/freepeepee/MigrationV5IT.kt`

**Interfaces:**
- Consumes: existing `toilet` + `app_user` tables; the bootstrapped `admin` from Task 1.
- Produces: no `matvs` row; toilet rows intact with `created_by` NULL where they referenced `matvs`. Test login creds are now `admin` / `test-admin-pw`.

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V5__public_readonly_admin.sql`:

```sql
-- V5__public_readonly_admin.sql
-- Public read-only model: the seeded dev user is removed and the real admin is provisioned
-- from environment variables at startup. Relax FKs so deleting a user never destroys
-- toilet rows or audit history (audit_entry.actor_name is denormalized text and survives).

ALTER TABLE toilet DROP CONSTRAINT IF EXISTS toilet_created_by_fkey;
ALTER TABLE toilet ADD CONSTRAINT toilet_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE audit_entry DROP CONSTRAINT IF EXISTS audit_entry_actor_id_fkey;
ALTER TABLE audit_entry ADD CONSTRAINT audit_entry_actor_id_fkey
    FOREIGN KEY (actor_id) REFERENCES app_user(id) ON DELETE SET NULL;

-- Remove the dev-seeded credentials (matvs / lap00p00). Toilets' created_by becomes NULL.
DELETE FROM app_user WHERE username = 'matvs';
```

- [ ] **Step 2: Write the failing test**

Create `backend/src/test/kotlin/dev/matvs/freepeepee/MigrationV5IT.kt`:

```kotlin
package dev.matvs.freepeepee

import dev.matvs.freepeepee.repository.ToiletRepository
import dev.matvs.freepeepee.repository.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration

@SpringBootTest
@ContextConfiguration(initializers = [PostgisInitializer::class])
class MigrationV5IT(
    @Autowired private val users: UserRepository,
    @Autowired private val toilets: ToiletRepository
) : DescribeSpec({
    describe("V5 migration") {
        it("removes the seeded matvs user") {
            users.findByUsername("matvs").shouldBeNull()
        }
        it("preserves the seeded toilet rows") {
            toilets.findAll().size shouldBeGreaterThanOrEqualTo 7
        }
    }
})
```

- [ ] **Step 3: Run it to confirm it fails**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.MigrationV5IT"`
Expected: FAIL — without V5, `matvs` still exists (the "removes matvs" expectation fails). *(If Testcontainers reuse keeps an old DB volume, run `docker rm -f` on the reused container or disable reuse for this run so V5 applies to a fresh schema.)*

- [ ] **Step 4: Update the existing login-based tests to use the admin creds**

In `AuthFlowIT.kt`, replace the three credential literals:
- `LoginRequest("matvs", "lap00p00", rememberMe = true)` → `LoginRequest("admin", "test-admin-pw", rememberMe = true)`
- `LoginRequest("matvs", "wrong", rememberMe = false)` → `LoginRequest("admin", "wrong", rememberMe = false)`
- the "unknown user" case `LoginRequest("ghost", ...)` stays unchanged.

In `ToiletApiIT.kt`, update the `token()` helper literal:
- `LoginRequest("matvs", "lap00p00", false)` → `LoginRequest("admin", "test-admin-pw", false)`

- [ ] **Step 5: Run the migration + login tests**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.MigrationV5IT" --tests "dev.matvs.freepeepee.AuthFlowIT" --tests "dev.matvs.freepeepee.ToiletApiIT"`
Expected: PASS (MigrationV5IT, AuthFlowIT pass; ToiletApiIT still has its old "rejects unauthenticated read" test passing because security isn't changed until Task 3).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V5__public_readonly_admin.sql \
        backend/src/test/kotlin/dev/matvs/freepeepee/MigrationV5IT.kt \
        backend/src/test/kotlin/dev/matvs/freepeepee/AuthFlowIT.kt \
        backend/src/test/kotlin/dev/matvs/freepeepee/ToiletApiIT.kt
git commit -m "feat(backend): V5 migration removes matvs, preserves toilet/audit data

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Public reads, admin-only writes (backend security)

**Files:**
- Modify: `backend/src/main/kotlin/dev/matvs/freepeepee/config/SecurityConfig.kt:32-36`
- Modify: `backend/src/test/kotlin/dev/matvs/freepeepee/ToiletApiIT.kt`

**Interfaces:**
- Consumes: `JwtAuthFilter` stamping `ROLE_ADMIN`.
- Produces: `GET /api/toilets/**` public; `POST/PUT/DELETE /api/toilets/**` and `GET /api/audit/**` require `ROLE_ADMIN`.

- [ ] **Step 1: Rewrite the unauthenticated-read test as a public-read test, add a write-protection test**

In `ToiletApiIT.kt`, replace the `it("rejects unauthenticated read")` block with these two:

```kotlin
        it("allows unauthenticated read") {
            mvc.get("/api/toilets").andExpect { status { isOk() } }
        }

        it("rejects unauthenticated create") {
            val body = mapper.writeValueAsString(ToiletCreateRequest(
                name = "No-auth loo", address = "Nowhere 1, Zurich",
                lat = 47.37, lon = 8.54, isWorking = true,
                toiletType = ToiletType.OTHER, notes = null
            ))
            mvc.post("/api/toilets") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isUnauthorized() } }
        }
```

(The existing "creates a toilet … with token" test already proves admin writes succeed.)

- [ ] **Step 2: Run to confirm the new public-read test fails**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.ToiletApiIT"`
Expected: FAIL — `GET /api/toilets` currently returns 401, not 200.

- [ ] **Step 3: Update `SecurityConfig` authorization rules**

Replace the `.authorizeHttpRequests { ... }` block (lines 32–36) with:

```kotlin
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
              .requestMatchers("/actuator/health/**").permitAll()
              .requestMatchers(HttpMethod.GET, "/api/toilets/**").permitAll()
              .requestMatchers("/api/toilets/**").hasRole("ADMIN")
              .requestMatchers("/api/audit/**").hasRole("ADMIN")
              .anyRequest().authenticated()
        }
```

- [ ] **Step 4: Run to confirm pass**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.ToiletApiIT"`
Expected: PASS (public read 200; unauthenticated create 401; admin create 201; nearby seed test 200).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/dev/matvs/freepeepee/config/SecurityConfig.kt \
        backend/src/test/kotlin/dev/matvs/freepeepee/ToiletApiIT.kt
git commit -m "feat(backend): public toilet reads, admin-only writes

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Expose role in the login response (backend)

**Files:**
- Modify: `backend/src/main/kotlin/dev/matvs/freepeepee/web/dto/Dtos.kt:75`
- Modify: `backend/src/main/kotlin/dev/matvs/freepeepee/web/Controllers.kt:21-23`
- Modify: `backend/src/test/kotlin/dev/matvs/freepeepee/AuthFlowIT.kt`

**Interfaces:**
- Consumes: `AuthResult.Success(tokens, role)` (already carries `role: String`).
- Produces: login JSON now includes `"role": "ADMIN"`.

- [ ] **Step 1: Add a role assertion to the success login test**

In `AuthFlowIT.kt`, inside the existing "returns 200 + token" block, after the `refreshToken` assertion add:

```kotlin
                tree["role"].asText() shouldBe "ADMIN"
```

Add the import: `import io.kotest.matchers.shouldBe`.

- [ ] **Step 2: Run to confirm it fails**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.AuthFlowIT"`
Expected: FAIL — response has no `role` field yet (`tree["role"]` is null).

- [ ] **Step 3: Add `role` to `TokenResponse`**

In `Dtos.kt`, change the last line:

```kotlin
data class TokenResponse(val accessToken: String, val refreshToken: String?, val expiresAtEpoch: Long, val role: String)
```

- [ ] **Step 4: Pass role through in `AuthController`**

In `Controllers.kt`, change the `AuthResult.Success` branch:

```kotlin
            is AuthResult.Success -> ResponseEntity.ok(
                TokenResponse(r.tokens.access, r.tokens.refresh, r.tokens.expiresAt.epochSecond, r.role)
            )
```

- [ ] **Step 5: Run to confirm pass**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.AuthFlowIT"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/dev/matvs/freepeepee/web/dto/Dtos.kt \
        backend/src/main/kotlin/dev/matvs/freepeepee/web/Controllers.kt \
        backend/src/test/kotlin/dev/matvs/freepeepee/AuthFlowIT.kt
git commit -m "feat(backend): include role in login response

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Audit viewer endpoint (backend)

**Files:**
- Modify: `backend/src/main/kotlin/dev/matvs/freepeepee/repository/Repositories.kt:52-53`
- Modify: `backend/src/main/kotlin/dev/matvs/freepeepee/web/dto/Dtos.kt`
- Create: `backend/src/main/kotlin/dev/matvs/freepeepee/web/AuditController.kt`
- Test: `backend/src/test/kotlin/dev/matvs/freepeepee/AuditApiIT.kt`

**Interfaces:**
- Consumes: `AuditRepository`, `AuditEntry`, `AuditOperation`, the `token()` admin-login pattern.
- Produces: `GET /api/audit?page&size` → Spring `Page<AuditDto>`; `AuditDto(occurredAt, actorName, operation, entityType, entityId, fieldName, oldValue, newValue, actorIp)`.

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/kotlin/dev/matvs/freepeepee/AuditApiIT.kt`:

```kotlin
package dev.matvs.freepeepee

import com.fasterxml.jackson.databind.ObjectMapper
import dev.matvs.freepeepee.domain.ToiletType
import dev.matvs.freepeepee.web.dto.LoginRequest
import dev.matvs.freepeepee.web.dto.ToiletCreateRequest
import io.kotest.core.spec.style.DescribeSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ContextConfiguration(initializers = [PostgisInitializer::class])
class AuditApiIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper
) : DescribeSpec({

    fun token(): String {
        val res = mvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(LoginRequest("admin", "test-admin-pw", false))
        }.andReturn().response.contentAsString
        return mapper.readTree(res)["accessToken"].asText()
    }

    describe("GET /api/audit") {
        it("rejects anonymous access") {
            mvc.get("/api/audit").andExpect { status { isUnauthorized() } }
        }

        it("returns audit rows for an admin after a write") {
            val t = token()
            mvc.post("/api/toilets") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $t")
                content = mapper.writeValueAsString(ToiletCreateRequest(
                    name = "Audited loo", address = "Audit St 1, Zurich",
                    lat = 47.37, lon = 8.54, isWorking = true,
                    toiletType = ToiletType.OTHER, notes = null
                ))
            }.andExpect { status { isCreated() } }

            mvc.get("/api/audit?page=0&size=10") {
                header("Authorization", "Bearer $t")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content") { isArray() }
                jsonPath("$.content[0].operation") { exists() }
            }
        }
    }
})
```

- [ ] **Step 2: Run to confirm it fails**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.AuditApiIT"`
Expected: FAIL — no `/api/audit` mapping (404/401 on the admin call).

- [ ] **Step 3: Add the paged repository query**

In `Repositories.kt`, add the import and method. Change the `AuditRepository` interface:

```kotlin
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
```

```kotlin
@Repository
interface AuditRepository : JpaRepository<AuditEntry, Long> {
    fun findAllByOrderByOccurredAtDesc(pageable: Pageable): Page<AuditEntry>
}
```

- [ ] **Step 4: Add `AuditDto`**

Append to `Dtos.kt`:

```kotlin
import dev.matvs.freepeepee.domain.AuditEntry
import dev.matvs.freepeepee.domain.AuditOperation
```

```kotlin
data class AuditDto(
    val occurredAt: OffsetDateTime,
    val actorName: String,
    val operation: AuditOperation,
    val entityType: String,
    val entityId: UUID?,
    val fieldName: String?,
    val oldValue: String?,
    val newValue: String?,
    val actorIp: String?
) {
    companion object {
        fun from(e: AuditEntry) = AuditDto(
            occurredAt = e.occurredAt, actorName = e.actorName, operation = e.operation,
            entityType = e.entityType, entityId = e.entityId, fieldName = e.fieldName,
            oldValue = e.oldValue, newValue = e.newValue, actorIp = e.actorIp
        )
    }
}
```

- [ ] **Step 5: Create `AuditController`**

Create `backend/src/main/kotlin/dev/matvs/freepeepee/web/AuditController.kt`:

```kotlin
package dev.matvs.freepeepee.web

import dev.matvs.freepeepee.repository.AuditRepository
import dev.matvs.freepeepee.web.dto.AuditDto
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/audit")
class AuditController(private val repo: AuditRepository) {

    /** Newest-first audit entries. Authorization enforced by SecurityConfig (ROLE_ADMIN). */
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): Page<AuditDto> =
        repo.findAllByOrderByOccurredAtDesc(PageRequest.of(page, size.coerceIn(1, 200)))
            .map(AuditDto::from)
}
```

- [ ] **Step 6: Run to confirm pass**

Run: `cd backend && ./gradlew test --tests "dev.matvs.freepeepee.AuditApiIT"`
Expected: PASS.

- [ ] **Step 7: Run the full backend gate**

Run: `cd backend && ./gradlew build jacocoTestReport jacocoTestCoverageVerification`
Expected: BUILD SUCCESSFUL, coverage ≥ 0.75.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/kotlin/dev/matvs/freepeepee/repository/Repositories.kt \
        backend/src/main/kotlin/dev/matvs/freepeepee/web/dto/Dtos.kt \
        backend/src/main/kotlin/dev/matvs/freepeepee/web/AuditController.kt \
        backend/src/test/kotlin/dev/matvs/freepeepee/AuditApiIT.kt
git commit -m "feat(backend): admin-only audit log endpoint

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: AuthService role + isAdmin (frontend)

**Files:**
- Modify: `frontend/src/app/core/auth/auth.service.ts`
- Modify: `frontend/src/app/core/auth/auth.service.spec.ts`

**Interfaces:**
- Consumes: login JSON `{ accessToken, refreshToken, expiresAtEpoch, role }`.
- Produces: `AuthService.isAdmin: Signal<boolean>`, `AuthService.role: Signal<string|null>`, `logout(redirect = '/map')`.

- [ ] **Step 1: Update the spec for role/isAdmin and the new logout target**

Replace the three flush bodies and the logout test in `auth.service.spec.ts`:

```ts
  it('stores access token + role in localStorage on rememberMe=true', () => {
    svc.login('admin', 'pw', true).subscribe();
    const req = http.expectOne(r => r.url.endsWith('/api/auth/login'));
    req.flush({ accessToken: 'a.b.c', refreshToken: 'r.e.f', expiresAtEpoch: 9999999999, role: 'ADMIN' });
    expect(localStorage.getItem('fp.access')).toBe('a.b.c');
    expect(svc.isAuthenticated()).toBeTrue();
    expect(svc.isAdmin()).toBeTrue();
  });

  it('stores in sessionStorage when rememberMe=false', () => {
    svc.login('admin', 'pw', false).subscribe();
    http.expectOne(r => r.url.endsWith('/api/auth/login'))
        .flush({ accessToken: 'a.b.c', refreshToken: null, expiresAtEpoch: 9999999999, role: 'ADMIN' });
    expect(localStorage.getItem('fp.access')).toBeNull();
    expect(sessionStorage.getItem('fp.access')).toBe('a.b.c');
  });

  it('clears tokens and redirects to /map on logout', () => {
    localStorage.setItem('fp.access', 'x');
    sessionStorage.setItem('fp.access', 'y');
    svc.logout();
    expect(localStorage.getItem('fp.access')).toBeNull();
    expect(sessionStorage.getItem('fp.access')).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/map']);
  });

  it('redirects to a custom target on logout', () => {
    svc.logout('/admin');
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/admin']);
  });
```

- [ ] **Step 2: Run to confirm it fails**

Run: `cd frontend && npm test`
Expected: FAIL — `isAdmin` is undefined / logout navigates to `/login`.

- [ ] **Step 3: Implement role storage + isAdmin + parameterized logout**

In `auth.service.ts`:

Add the storage key next to the others:
```ts
const KEY_ROLE = 'fp.role';
```

Update the `TokenResponse` interface:
```ts
interface TokenResponse {
  accessToken: string;
  refreshToken: string | null;
  expiresAtEpoch: number;
  role: string;
}
```

Add the signals after `isAuthenticated`:
```ts
  readonly role = signal<string | null>(localStorage.getItem(KEY_ROLE) ?? sessionStorage.getItem(KEY_ROLE));
  readonly isAdmin = computed(() => this.isAuthenticated() && this.role() === 'ADMIN');
```

Replace `logout()`:
```ts
  logout(redirect = '/map'): void {
    [KEY_ACCESS, KEY_REFRESH, KEY_EXP, KEY_ROLE].forEach(k => {
      localStorage.removeItem(k);
      sessionStorage.removeItem(k);
    });
    this.token.set(null);
    this.role.set(null);
    this.router.navigate([redirect]);
  }
```

In `setTokens(...)`, persist + set the role (after the `KEY_EXP` line and before `this.token.set`):
```ts
    store.setItem(KEY_ROLE, t.role);
    ...
    this.token.set(t.accessToken);
    this.role.set(t.role);
```

- [ ] **Step 4: Run to confirm pass**

Run: `cd frontend && npm test`
Expected: PASS (AuthService suite).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/core/auth/auth.service.ts frontend/src/app/core/auth/auth.service.spec.ts
git commit -m "feat(frontend): expose isAdmin signal and role-aware logout

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Guards, routes, error interceptor (frontend)

**Files:**
- Create: `frontend/src/app/core/guards/admin.guard.ts`
- Create: `frontend/src/app/core/guards/admin.guard.spec.ts`
- Delete: `frontend/src/app/core/guards/auth.guard.ts`
- Modify: `frontend/src/app/core/guards/guest.guard.ts`
- Modify: `frontend/src/app/core/http/error.interceptor.ts`
- Modify: `frontend/src/app/app.routes.ts`
- Modify: `frontend/src/app/features/login/login.component.ts:59`

**Interfaces:**
- Consumes: `AuthService.isAdmin`, `AuthService.logout(redirect)`.
- Produces: `adminGuard` (→ `/admin` if not admin), retargeted `guestGuard` (→ `/map` if admin); routes `/admin`, `/`, `/log`.

- [ ] **Step 1: Write the admin guard spec**

Create `frontend/src/app/core/guards/admin.guard.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { runInInjectionContext, Injector } from '@angular/core';
import { adminGuard } from './admin.guard';
import { AuthService } from '../auth/auth.service';

describe('adminGuard', () => {
  function run(isAdmin: boolean) {
    const auth = { isAdmin: () => isAdmin } as Partial<AuthService>;
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: auth }]
    });
    const injector = TestBed.inject(Injector);
    return runInInjectionContext(injector, () => adminGuard({} as any, {} as any));
  }

  it('allows admins', () => {
    expect(run(true)).toBeTrue();
  });

  it('redirects non-admins to /admin', () => {
    const result = run(false);
    expect(result instanceof UrlTree).toBeTrue();
    expect((result as UrlTree).toString()).toBe('/admin');
  });
});
```

- [ ] **Step 2: Run to confirm it fails**

Run: `cd frontend && npm test`
Expected: FAIL — `admin.guard` module does not exist.

- [ ] **Step 3: Create `admin.guard.ts`**

```ts
import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../auth/auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isAdmin() ? true : router.createUrlTree(['/admin']);
};
```

- [ ] **Step 4: Retarget `guest.guard.ts`**

Replace its body's return:
```ts
  return auth.isAdmin() ? router.createUrlTree(['/map']) : true;
```

- [ ] **Step 5: Delete the obsolete auth guard**

```bash
git rm frontend/src/app/core/guards/auth.guard.ts
```

- [ ] **Step 6: Update `error.interceptor.ts`**

Replace the `catchError` body:
```ts
    catchError((err: HttpErrorResponse) => {
      if ((err.status === 401 || err.status === 403) && !req.url.endsWith('/api/auth/login')) {
        auth.logout('/admin');
      }
      return throwError(() => err);
    })
```

- [ ] **Step 7: Rewrite `app.routes.ts`**

```ts
import { Routes } from '@angular/router';
import { guestGuard } from './core/guards/guest.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: 'admin',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    loadComponent: () => import('./features/shell/shell.component').then(m => m.ShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'map' },
      {
        path: 'list',
        loadComponent: () => import('./features/toilets-list/toilets-list.component').then(m => m.ToiletsListComponent)
      },
      {
        path: 'map',
        loadComponent: () => import('./features/toilets-map/toilets-map.component').then(m => m.ToiletsMapComponent)
      },
      {
        path: 'log',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/audit-log/audit-log.component').then(m => m.AuditLogComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];
```

- [ ] **Step 8: Point login success at `/map`**

In `login.component.ts`, change the success navigation:
```ts
      next: () => this.router.navigate(['/map']),
```

- [ ] **Step 9: Run guard tests**

Run: `cd frontend && npm test`
Expected: PASS (adminGuard suite). *(Build verification for the `/log` lazy route happens in Task 11 once the component exists; until then `npm run build` will fail on the missing import — that's expected and resolved in Task 11.)*

- [ ] **Step 10: Commit**

```bash
git add frontend/src/app/core/guards/ frontend/src/app/core/http/error.interceptor.ts \
        frontend/src/app/app.routes.ts frontend/src/app/features/login/login.component.ts
git commit -m "feat(frontend): public shell, /admin login route, admin guard, audit route

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Shell — admin-only nav & sign-out (frontend)

**Files:**
- Modify: `frontend/src/app/features/shell/shell.component.ts`
- Modify: `frontend/src/app/features/shell/shell.component.pug`

**Interfaces:**
- Consumes: `AuthService.isAdmin`, `AuthService.logout()`.
- Produces: public toolbar = brand + map/list toggle only; admin toolbar adds `log` nav + sign-out.

- [ ] **Step 1: Expose `isAdmin` from the shell component**

In `shell.component.ts`, add inside the class (keep existing `logout()` and `currentRoute`):
```ts
  readonly isAdmin = this.auth.isAdmin;
```
(`this.auth` is the already-injected `AuthService`.)

- [ ] **Step 2: Gate the admin controls in the template**

In `shell.component.pug`, replace the sign-out button line with a log nav button + a gated sign-out:
```pug
    button(*ngIf="isAdmin()", mat-icon-button, routerLink="/log", routerLinkActive="fp-shell__nav-active", matTooltip="change log", aria-label="change log")
      mat-icon history
    button(*ngIf="isAdmin()", mat-icon-button, (click)="logout()", matTooltip="sign out", aria-label="sign out")
      mat-icon logout
```
(`CommonModule` and `RouterLinkActive` are already imported in the shell.)

- [ ] **Step 3: Verify build/type-check**

Run: `cd frontend && npm run build`
Expected: this still fails only on the not-yet-created `audit-log.component` import from Task 7's routes; confirm there are **no other** errors (no shell template errors). The build goes green at the end of Task 11.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/shell/
git commit -m "feat(frontend): hide admin nav and sign-out from public users

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: List view — gate edit controls, copy & mobile polish (frontend)

**Files:**
- Modify: `frontend/src/app/features/toilets-list/toilets-list.component.ts`
- Modify: `frontend/src/app/features/toilets-list/toilets-list.component.pug`
- Modify: `frontend/src/app/features/toilets-list/toilets-list.component.scss`
- Test: `frontend/src/app/features/toilets-list/toilets-list.component.spec.ts` (create)

**Interfaces:**
- Consumes: `AuthService.isAdmin`.
- Produces: directions available to all; `new`/edit/delete only when admin; read-only empty-state copy.

- [ ] **Step 1: Write a control-visibility spec**

Create `frontend/src/app/features/toilets-list/toilets-list.component.spec.ts`:

```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { signal } from '@angular/core';
import { ToiletsListComponent } from './toilets-list.component';
import { AuthService } from '../../core/auth/auth.service';

function setup(isAdmin: boolean) {
  TestBed.configureTestingModule({
    imports: [ToiletsListComponent],
    providers: [
      provideHttpClient(),
      provideHttpClientTesting(),
      provideNoopAnimations(),
      { provide: AuthService, useValue: { isAdmin: signal(isAdmin) } }
    ]
  });
  const fixture = TestBed.createComponent(ToiletsListComponent);
  const http = TestBed.inject(HttpTestingController);
  // the constructor effect triggers an initial list() load
  http.match(r => r.url.endsWith('/api/toilets')).forEach(r => r.flush([]));
  fixture.detectChanges();
  return fixture;
}

describe('ToiletsListComponent control visibility', () => {
  it('hides the "new freepeepee" button for anonymous users', () => {
    const fixture = setup(false);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('new freepeepee');
  });

  it('shows the "new freepeepee" button for admins', () => {
    const fixture = setup(true);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('new freepeepee');
  });
});
```

- [ ] **Step 2: Run to confirm it fails**

Run: `cd frontend && npm test`
Expected: FAIL — the button is always rendered (no gating yet); the anonymous case fails.

- [ ] **Step 3: Inject `isAdmin` into the component**

In `toilets-list.component.ts`, add the import and field:
```ts
import { AuthService } from '../../core/auth/auth.service';
```
```ts
  readonly isAdmin = inject(AuthService).isAdmin;
```

- [ ] **Step 4: Gate the controls in the template**

In `toilets-list.component.pug`:

Gate the "new freepeepee" button:
```pug
      button(*ngIf="isAdmin()", mat-flat-button, color="primary", (click)="add()")
        mat-icon add
        span.fp-list__btn-label new freepeepee
```

In the `actions` column cell, keep directions for all and gate edit/delete:
```pug
        td.fp-list__actions-cell(mat-cell, *matCellDef="let t")
          button(mat-icon-button, (click)="navigate(t)", matTooltip="get directions")
            mat-icon directions
          button(*ngIf="isAdmin()", mat-icon-button, (click)="edit(t)", matTooltip="edit")
            mat-icon edit
          button(*ngIf="isAdmin()", mat-icon-button, color="warn", (click)="remove(t)", matTooltip="delete")
            mat-icon delete
```

In the mobile card actions, keep directions for all and gate edit/delete:
```pug
      .fp-list__card-actions
        button(mat-stroked-button, (click)="navigate(t)")
          mat-icon directions
          | directions
        button(*ngIf="isAdmin()", mat-stroked-button, (click)="edit(t)")
          mat-icon edit
          | edit
        button(*ngIf="isAdmin()", mat-stroked-button, color="warn", (click)="remove(t)")
          mat-icon delete
          | delete
```

Update **both** empty-state blocks (table-wrap and cards):
```pug
    .fp-list__empty(*ngIf="rows().length === 0")
      mat-icon inbox
      div {{ isAdmin() ? 'No freepeepees found. Add one.' : 'No freepeepees found.' }}
```

- [ ] **Step 5: Mobile toolbar polish (SCSS)**

Open `toilets-list.component.scss`, find the `.fp-list__top` rule. Ensure the action row wraps and stays tappable by adding (or merging) these declarations to `.fp-list__top` and its actions container:
```scss
.fp-list__top {
  display: flex;
  flex-wrap: wrap;          // prevent horizontal overflow on narrow screens
  gap: 8px;
  align-items: center;
}
.fp-list__top-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;

  button { min-height: 40px; }   // comfortable tap target on mobile
}
```
(If the file already sets `display:flex` on these selectors, just add the `flex-wrap`, `gap`, and `min-height` lines rather than duplicating the block.)

- [ ] **Step 6: Run the component test**

Run: `cd frontend && npm test`
Expected: PASS (control-visibility suite).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/features/toilets-list/
git commit -m "feat(frontend): gate list edit controls behind admin, read-only copy, mobile polish

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 10: Map view — gate add button (frontend)

**Files:**
- Modify: `frontend/src/app/features/toilets-map/toilets-map.component.ts`
- Modify: `frontend/src/app/features/toilets-map/toilets-map.component.pug`

**Interfaces:**
- Consumes: `AuthService.isAdmin`.
- Produces: the map "new freepeepee" button shows only for admins; filter / find-near-me / directions stay public.

- [ ] **Step 1: Inject `isAdmin`**

In `toilets-map.component.ts`, add:
```ts
import { AuthService } from '../../core/auth/auth.service';
```
```ts
  readonly isAdmin = inject(AuthService).isAdmin;
```

- [ ] **Step 2: Gate the add button**

In `toilets-map.component.pug`, change the add button line:
```pug
      button(*ngIf="isAdmin()", mat-flat-button, color="accent", (click)="add()", matTooltip="add new freepeepee")
        mat-icon add
        span.fp-map__btn-label new freepeepee
```

- [ ] **Step 3: Type/template check**

Run: `cd frontend && npm run build`
Expected: still fails only on the missing `audit-log.component` import (Task 11); confirm no map template errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/features/toilets-map/
git commit -m "feat(frontend): hide map add button from public users

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 11: Audit-log viewer (frontend)

**Files:**
- Create: `frontend/src/app/core/services/audit.service.ts`
- Create: `frontend/src/app/features/audit-log/audit-log.component.ts`
- Create: `frontend/src/app/features/audit-log/audit-log.component.pug`
- Create: `frontend/src/app/features/audit-log/audit-log.component.scss`
- Test: `frontend/src/app/features/audit-log/audit-log.component.spec.ts`

**Interfaces:**
- Consumes: `GET /api/audit?page&size` → `{ content: AuditEntry[], number, last, totalElements }`.
- Produces: `AuditService.page(page, size): Observable<AuditPage>`; `AuditLogComponent` (route `/log`).

- [ ] **Step 1: Create the audit service**

`frontend/src/app/core/services/audit.service.ts`:
```ts
import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface AuditEntry {
  occurredAt: string;
  actorName: string;
  operation: string;
  entityType: string;
  entityId: string | null;
  fieldName: string | null;
  oldValue: string | null;
  newValue: string | null;
  actorIp: string | null;
}

export interface AuditPage {
  content: AuditEntry[];
  number: number;
  size: number;
  totalElements: number;
  last: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.api}/api/audit`;

  page(page = 0, size = 50): Observable<AuditPage> {
    return this.http.get<AuditPage>(this.base, { params: { page, size } });
  }
}
```

- [ ] **Step 2: Write the component spec**

`frontend/src/app/features/audit-log/audit-log.component.spec.ts`:
```ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { AuditLogComponent } from './audit-log.component';

describe('AuditLogComponent', () => {
  it('renders rows returned by the API', () => {
    TestBed.configureTestingModule({
      imports: [AuditLogComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations()]
    });
    const fixture = TestBed.createComponent(AuditLogComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(r => r.url.endsWith('/api/audit')).flush({
      content: [{
        occurredAt: '2026-06-19T10:00:00Z', actorName: 'admin', operation: 'CREATE',
        entityType: 'Toilet', entityId: 'abc', fieldName: null, oldValue: null,
        newValue: '{...}', actorIp: '127.0.0.1'
      }],
      number: 0, size: 50, totalElements: 1, last: true
    });
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('admin');
    expect(text).toContain('CREATE');
  });
});
```

- [ ] **Step 3: Run to confirm it fails**

Run: `cd frontend && npm test`
Expected: FAIL — `audit-log.component` does not exist.

- [ ] **Step 4: Create the component**

`frontend/src/app/features/audit-log/audit-log.component.ts`:
```ts
import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuditEntry, AuditService } from '../../core/services/audit.service';

@Component({
  selector: 'fp-audit-log',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatButtonModule, MatIconModule],
  templateUrl: './audit-log.component.pug',
  styleUrl: './audit-log.component.scss'
})
export class AuditLogComponent {
  private readonly api = inject(AuditService);

  readonly cols = ['occurredAt', 'actorName', 'operation', 'entityType', 'fieldName', 'change'];
  readonly rows = signal<AuditEntry[]>([]);
  readonly last = signal(true);
  private page = 0;

  constructor() { this.load(0); }

  private load(page: number): void {
    this.api.page(page, 50).subscribe(p => {
      this.rows.update(cur => page === 0 ? p.content : [...cur, ...p.content]);
      this.page = p.number;
      this.last.set(p.last);
    });
  }

  loadMore(): void { if (!this.last()) this.load(this.page + 1); }
}
```

`frontend/src/app/features/audit-log/audit-log.component.pug`:
```pug
.fp-log
  h2.fp-log__title change log

  //- Desktop table
  .fp-log__table-wrap
    table.fp-log__table(mat-table, [dataSource]="rows()")
      ng-container(matColumnDef="occurredAt")
        th(mat-header-cell, *matHeaderCellDef) when
        td(mat-cell, *matCellDef="let r") {{ r.occurredAt | date:'short' }}
      ng-container(matColumnDef="actorName")
        th(mat-header-cell, *matHeaderCellDef) actor
        td(mat-cell, *matCellDef="let r") {{ r.actorName }}
      ng-container(matColumnDef="operation")
        th(mat-header-cell, *matHeaderCellDef) op
        td(mat-cell, *matCellDef="let r") {{ r.operation }}
      ng-container(matColumnDef="entityType")
        th(mat-header-cell, *matHeaderCellDef) entity
        td(mat-cell, *matCellDef="let r") {{ r.entityType }}
      ng-container(matColumnDef="fieldName")
        th(mat-header-cell, *matHeaderCellDef) field
        td(mat-cell, *matCellDef="let r") {{ r.fieldName || '—' }}
      ng-container(matColumnDef="change")
        th(mat-header-cell, *matHeaderCellDef) change
        td(mat-cell, *matCellDef="let r") {{ r.oldValue || '∅' }} → {{ r.newValue || '∅' }}
      tr(mat-header-row, *matHeaderRowDef="cols; sticky: true")
      tr(mat-row, *matRowDef="let row; columns: cols")

  //- Mobile cards
  .fp-log__cards
    .fp-log__card(*ngFor="let r of rows()")
      .fp-log__card-top
        span.fp-log__card-op {{ r.operation }}
        span.fp-log__card-when {{ r.occurredAt | date:'short' }}
      .fp-log__card-line {{ r.actorName }} · {{ r.entityType }}{{ r.fieldName ? ' · ' + r.fieldName : '' }}
      .fp-log__card-change(*ngIf="r.fieldName") {{ r.oldValue || '∅' }} → {{ r.newValue || '∅' }}

  .fp-log__empty(*ngIf="rows().length === 0")
    mat-icon inbox
    div No changes recorded yet.

  .fp-log__more(*ngIf="!last()")
    button(mat-stroked-button, (click)="loadMore()") load more
```

`frontend/src/app/features/audit-log/audit-log.component.scss`:
```scss
.fp-log {
  padding: 16px;

  &__title { margin: 0 0 12px; font-weight: 600; }

  &__table-wrap { overflow-x: auto; }
  &__table { width: 100%; }

  // Desktop table vs mobile cards toggle, matching the list view breakpoint
  &__cards { display: none; }

  &__empty {
    display: flex; flex-direction: column; align-items: center;
    gap: 8px; padding: 32px; opacity: .7;
  }

  &__more { display: flex; justify-content: center; padding: 16px; }
}

@media (max-width: 640px) {
  .fp-log__table-wrap { display: none; }
  .fp-log__cards {
    display: flex; flex-direction: column; gap: 8px;

    .fp-log__card {
      border: 1px solid rgba(0,0,0,.12); border-radius: 8px; padding: 12px;
      display: flex; flex-direction: column; gap: 4px;
    }
    .fp-log__card-top { display: flex; justify-content: space-between; font-weight: 600; }
    .fp-log__card-when { font-weight: 400; opacity: .7; }
    .fp-log__card-change { font-family: monospace; word-break: break-word; }
  }
}
```

- [ ] **Step 5: Run the component test**

Run: `cd frontend && npm test`
Expected: PASS (audit-log suite).

- [ ] **Step 6: Full frontend build + test**

Run: `cd frontend && npm run build && npm test`
Expected: build SUCCESS (the `/log` lazy import now resolves) and all specs PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/core/services/audit.service.ts frontend/src/app/features/audit-log/
git commit -m "feat(frontend): admin audit-log viewer at /log

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 12: Config, compose & docs

**Files:**
- Modify: `.env.example`
- Modify: `.env` (local only — not committed)
- Modify: `docker-compose.yml:24-31`
- Modify: `README.md`
- Modify: `docs/runbook.md`

**Interfaces:**
- Consumes: `AdminUserInitializer` reading `ADMIN_USERNAME` / `ADMIN_PASSWORD`.
- Produces: documented public/admin model and required env vars.

- [ ] **Step 1: Add placeholders to `.env.example`**

Append:
```bash
# Default admin account (provisioned at startup). Reach the admin UI at /admin.
# Set real values in .env (never commit them).
ADMIN_USERNAME=admin
ADMIN_PASSWORD=changeme_choose_a_strong_password
```

- [ ] **Step 2: Add real values to local `.env`** (do **not** `git add` this file)

Append to `.env`:
```bash
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<a strong password of your choosing>
```

- [ ] **Step 3: Pass the vars to the `api` service in `docker-compose.yml`**

In the `api:` service `environment:` block, add (after `CORS_ORIGINS`):
```yaml
      ADMIN_USERNAME: ${ADMIN_USERNAME:?required}
      ADMIN_PASSWORD: ${ADMIN_PASSWORD:?required}
```

- [ ] **Step 4: Update README**

Replace the Quickstart sign-in line ("Open http://localhost:4200, … sign in as **matvs** / **lap00p00**.") with:
```markdown
Open http://localhost:4200 — the catalogue is **public and read-only**. To edit, go to
`http://localhost:4200/admin` and sign in with the `ADMIN_USERNAME` / `ADMIN_PASSWORD`
you set in `.env`. There is intentionally no link to `/admin` in the UI.
```
Also update the Docker quickstart `cp .env.example .env` line note to mention filling in
`ADMIN_USERNAME` / `ADMIN_PASSWORD`, and remove the "no admin-only view of the audit log" item
from the "What this scaffold isn't" section (it now exists at `/log`).

- [ ] **Step 5: Update the runbook**

In `docs/runbook.md`, wherever it references the seeded `matvs` user or login, document that the
admin is provisioned from `ADMIN_USERNAME` / `ADMIN_PASSWORD` on container start, that the public
site needs no login, and that `/admin` is the (unlinked) admin entry point. *(Read the file first;
edit the relevant credential/login lines to match.)*

- [ ] **Step 6: Verify nothing secret is staged**

Run: `git status --porcelain && git ls-files .env`
Expected: `.env` is **not** listed by `git ls-files`; `git status` shows only `.env.example`,
`docker-compose.yml`, `README.md`, `docs/runbook.md` as changes.

- [ ] **Step 7: Commit**

```bash
git add .env.example docker-compose.yml README.md docs/runbook.md
git commit -m "docs+config: env-defined admin, public read-only model, /admin entry point

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 13: Full verification & PR

**Files:** none (verification + integration).

- [ ] **Step 1: Backend full gate**

Run: `cd backend && ./gradlew build jacocoTestReport jacocoTestCoverageVerification`
Expected: BUILD SUCCESSFUL; coverage ≥ 0.75.

- [ ] **Step 2: Frontend build + tests**

Run: `cd frontend && npm run build && npm test`
Expected: build SUCCESS; all specs PASS. *(If headless Chrome is unavailable locally, note it and rely on the frontend CI workflow; the build must still pass.)*

- [ ] **Step 3: Manual smoke (docker compose)** — optional but recommended

Run: `docker compose up -d --build`, then:
- `curl -s localhost:8081/api/toilets | head` → returns JSON array **without** a token (public read).
- `curl -s -o /dev/null -w "%{http_code}" -X POST localhost:8081/api/toilets -H 'Content-Type: application/json' -d '{}'` → `401`.
- Browse `http://localhost:8081` → map/list visible, no add/edit/delete, no sign-out.
- Browse `http://localhost:8081/admin` → login; sign in with `.env` creds → add/edit/delete + `log` nav appear; `/log` shows entries after an edit.
Tear down: `docker compose down`.

- [ ] **Step 4: Push and open the PR**

```bash
git push -u origin feature/public-readonly-admin
gh pr create --base main --head feature/public-readonly-admin \
  --title "Public read-only catalogue + separate /admin" \
  --body "$(cat <<'EOF'
Implements the public read-only model with a separate, unlinked /admin surface.

- Public, tokenless reads on `GET /api/toilets/**`; writes + `GET /api/audit/**` require ROLE_ADMIN.
- Admin account provisioned at startup from `ADMIN_USERNAME` / `ADMIN_PASSWORD` (no secret in repo).
- V5 migration removes the seeded `matvs` user; toilet rows + audit history preserved via `ON DELETE SET NULL`.
- SPA: public shell, login moved to `/admin`, edit controls gated on `isAdmin`, new `/log` audit viewer.
- GUI polish: read-only empty-state copy, admin-only nav/sign-out, mobile toolbar wrapping.

Spec: docs/superpowers/specs/2026-06-19-public-readonly-admin-design.md

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-review notes

- **Spec coverage:** public reads (T3) · `/admin` only, unlinked (T7 routes + T8 no public link) · admin-only edit (T3 backend, T8–T10 frontend) · audit "like before" + viewer (audit logging untouched; T5 + T11) · env admin, no repo secret (T1, T12, T12-S6 check) · delete matvs + preserve data (T2) · GUI polish (T8 copy/nav, T9 mobile + empty-state, T10) · cleanup (auth.guard deleted T7; matvs seed removed T2; `Role.USER` intentionally retained per spec). All covered.
- **Type consistency:** `isAdmin()` (signal call) used uniformly in templates; `AuditEntry`/`AuditPage` fields match `AuditDto`/Spring `Page` JSON (`content`, `number`, `last`, `totalElements`); `logout(redirect='/map')` signature matches both shell (no arg) and interceptor (`'/admin'`) callers; `findAllByOrderByOccurredAtDesc` used identically in repo + controller.
- **Ordering caveat (called out in T2-S3):** Testcontainers `withReuse(true)` can retain a DB where `matvs` predates V5; if MigrationV5IT sees a stale schema, drop the reused container so V5 applies fresh.
