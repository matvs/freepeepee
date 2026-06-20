# Public read-only access + separate `/admin` — Design

**Date:** 2026-06-19
**Branch:** `feature/public-readonly-admin`
**Status:** Approved for planning

## Goal / business requirement

Turn freepeepee from a fully login-gated app into a **public read-only catalogue** with a
**separate, hidden admin surface**:

- Anyone can browse the toilet list and map without logging in (read-only).
- Editing (add / update / delete) is restricted to admins.
- Admin access is reached **only** by manually navigating to `/admin` — no link or button
  anywhere in the public UI.
- "What was a regular user before is now an admin user." Admin uses the **same** map/list views,
  but with the edit controls unlocked.
- The change-log / audit trail keeps working for admin actions, **and** admins get a new
  screen to browse it.
- The default admin is defined **only** via environment variables (`ADMIN_USERNAME`,
  `ADMIN_PASSWORD`). **No secret may live in the git repository.**
- The seeded dev user `matvs` / `lap00p00` is removed; the env-defined admin replaces it.
- Existing production data (the `toilet` rows) **must be preserved** across the upgrade.
- Fix GUI bugs/glitches surfaced by the read-only change, on mobile and web.
- Clean up dead/old-user code.

## Non-goals

- No password reset / self-service signup / multi-admin management UI.
- No change to the geospatial search, PWA, or CI/CD topology.
- No rip-out of the `Role` enum's `USER` value (see Decisions).

---

## Key decisions

### A. Admin provisioning — runtime bootstrap (chosen)

A Spring `ApplicationRunner` (`AdminUserInitializer`) reads `ADMIN_USERNAME` / `ADMIN_PASSWORD`
on every startup and **upserts** the admin `app_user` row, BCrypt-hashing the password with the
existing `PasswordEncoder` (cost 12).

- Fresh DB and existing DB behave identically.
- Password rotation = edit `.env` + restart.
- Fails fast with a clear error if either variable is blank (prevents a silently
  admin-less deployment).

Rejected: baking credentials into a Flyway migration — can't rotate, awkward secret handling,
and would put a hash in the repo.

### B. Removing `matvs` without losing data — additive migration + `ON DELETE SET NULL` (chosen)

`V2__seed.sql` already ran on production, so its checksum is frozen — it **must not** be edited.
A new migration `V5__public_readonly_admin.sql`:

1. Relaxes FK `toilet.created_by → app_user(id)` to `ON DELETE SET NULL`.
2. Relaxes FK `audit_entry.actor_id → app_user(id)` to `ON DELETE SET NULL`.
3. `DELETE FROM app_user WHERE username = 'matvs';`

Result: toilet rows survive (`created_by` becomes `NULL`); audit history survives because
`actor_name` is already denormalized text. Flyway runs **before** the `ApplicationRunner`, so
ordering is safe.

Decisions confirmed with stakeholder:
- `Role.USER` enum value is **left in place** (DB column default) but unused — avoids a risky
  enum/DDL change while no code path grants it.
- Existing toilets' `created_by` becomes `NULL` rather than being re-pointed to the new admin
  (the field is never surfaced in the UI or DTO).

---

## Backend changes

### Authorization (`SecurityConfig`)

| Endpoint | Access |
|---|---|
| `POST /api/auth/login` | public |
| `GET /actuator/health/**` | public |
| `GET /api/toilets/**` (list, get, nearby, search) | **public** |
| `POST/PUT/DELETE /api/toilets/**` | `hasRole('ADMIN')` |
| `GET /api/audit/**` | `hasRole('ADMIN')` |
| anything else | authenticated |

`JwtAuthFilter` already stamps `ROLE_ADMIN`. Reads require no token. Writes without a valid admin
token return 401 (no/invalid token) or 403 (token without ADMIN).

### Admin bootstrap & data migration

- New config keys: `freepeepee.admin.username: ${ADMIN_USERNAME:}` and
  `freepeepee.admin.password: ${ADMIN_PASSWORD:}` in `application.yml`.
- `AdminUserInitializer(users, encoder, @Value username, @Value password) : ApplicationRunner`:
  - blank username or password → throw with an actionable message.
  - user missing → create as `ADMIN`, enabled, hashed password.
  - user present → update password hash, force `role = ADMIN`, `isEnabled = true`.
- `V5__public_readonly_admin.sql` as described in Decision B (use `DROP CONSTRAINT IF EXISTS`
  with the Postgres default names `toilet_created_by_fkey`, `audit_entry_actor_id_fkey`).

### Audit viewer API

- `AuditController`: `GET /api/audit?page=0&size=50` (admin-only), newest-first.
- `AuditRepository.findAllByOrderByOccurredAtDesc(pageable): Page<AuditEntry>`.
- `AuditDto`: `occurredAt, actorName, operation, entityType, entityId, fieldName, oldValue,
  newValue, actorIp`. Returned as a Spring `Page<AuditDto>`.

### Login response

- `TokenResponse` gains `role: String`. `AuthService.AuthResult.Success` already carries the
  role; `AuthController` passes it through so the SPA can confirm admin status.

---

## Frontend changes

### Routing & access

```
/admin   -> AdminLoginComponent  (guestGuard: already-admin -> /map)   [existing login screen]
/        -> ShellComponent        (PUBLIC, no guard)
   ''    -> redirect 'map'
   map   -> ToiletsMapComponent   (public)
   list  -> ToiletsListComponent  (public)
   log   -> AuditLogComponent     (adminGuard: not-admin -> /admin)
**       -> redirect ''
```

- The `login` feature is reused as the `/admin` screen (duck animation kept). Route renamed to
  `/admin`; on success → `/map`.
- `AuthService`: store `role` from login; expose `isAdmin = computed(isAuthenticated && role==='ADMIN')`.
- `adminGuard` (authenticated→ok else `/admin`) replaces the old `authGuard`. `guestGuard`
  retargets to `/map`.
- `error.interceptor`: on 401/403 not on the login URL → `logout()` + redirect `/admin`. Public
  GETs no longer 401, so there is no spurious logout for anonymous visitors.

### Read-only gating & GUI polish

- Add/edit/delete buttons, the table `actions` column, and card action buttons render **only when
  `isAdmin`** (list, map, table, mobile cards).
- Shell toolbar: `sign out` button and a `log` nav item appear **only when admin**; the public
  toolbar is just brand + map/list toggle.
- `logout()` navigates to public `/map`, not `/login`.
- Empty-state text drops "Add one." for non-admins → "No freepeepees found."
- Mobile toolbar crowding: tidy the list/map action-row wrapping and tap-target sizing in SCSS
  (less acute now that the public toolbar has fewer buttons). Small contrast/focus nits fixed if
  found during implementation.
- `AuditLogComponent`: responsive (table on desktop, cards on mobile) styled like the existing
  list; columns time / actor / operation / entity / field / change (old → new). Paging via a
  "load more" button that fetches the next page from the API (simplest, mobile-friendly).

---

## Config / deploy / docs

- `.env.example`: add `ADMIN_USERNAME` and `ADMIN_PASSWORD` **placeholders** (no real values).
- Local `.env`: add real values. `.env` is already gitignored and untracked (verified:
  `git ls-files` shows only `.env.example`). **No secret enters the repo.**
- `docker-compose.yml`: pass `ADMIN_USERNAME: ${ADMIN_USERNAME:?required}` and
  `ADMIN_PASSWORD: ${ADMIN_PASSWORD:?required}` to the `api` service.
- README + `docs/runbook.md`: replace "sign in as matvs / lap00p00" with "the catalogue is public
  read-only; admins navigate to `/admin` and sign in with the credentials from `.env`."

---

## Testing strategy (TDD)

Test admin credentials are supplied via `backend/src/test/resources/application.yml`
(`freepeepee.admin.username` / `.password`, e.g. `admin` / a test-only password).

Backend (Kotest + Testcontainers, must keep ≥75% Jacoco gate):
- `AuthFlowIT`: log in with the env-provisioned admin creds (replace `matvs`/`lap00p00`);
  wrong password → 401; unknown user → 401.
- `ToiletApiIT`: flip "rejects unauthenticated read" → **allows** unauthenticated read (200);
  add "unauthenticated create → 401/403"; admin create still succeeds + audit row appears;
  nearby seed test unchanged (seed toilets still present after V5).
- New `AuditApiIT`: admin can `GET /api/audit`; anonymous request → 401/403.
- New `AdminUserInitializerTest` (or IT): admin user created/updated from configured creds.

Frontend (Karma + Jasmine):
- Keep existing `auth.service.spec` green; add specs for `adminGuard` / `guestGuard`.
- Component check: edit/add/delete controls are absent when not admin, present when admin.

---

## Gitflow

All work on `feature/public-readonly-admin` (isolated worktree during implementation),
merged to `main` via a single pull request once tests pass.

## Risks / watch-items

- Constraint names: confirm Postgres auto-named the V1 inline FKs `toilet_created_by_fkey` /
  `audit_entry_actor_id_fkey`; `DROP CONSTRAINT IF EXISTS` guards against mismatch on fresh vs
  existing DBs.
- Bootstrap ordering: Flyway (context init) runs before `ApplicationRunner` — verified by Spring
  Boot lifecycle, but worth an explicit test that login works after a cold boot.
- CSP `default-src 'self'` on the API is unchanged and does not affect the SPA (served by nginx),
  so the OSM map/tiles and Nominatim autocomplete keep working.
