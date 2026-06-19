# freepeepee – architecture

## Stack at a glance

| Layer | Tech | Notes |
|---|---|---|
| Frontend | Angular 18 (standalone), Angular Material, Leaflet, SCSS, Pug (via `@webdiscus/pug-loader`), Service Worker (`@angular/service-worker`) | PWA installable, works offline for previously-loaded tiles and toilet list. |
| Backend  | Spring Boot 3.3, Kotlin 1.9 (`-Xcontext-receivers`), Spring Security, Spring Data JPA, Hibernate Spatial 6.5, JJWT 0.12, Flyway 10. | Stateless API, BCrypt cost 12, JWT only. |
| Database | PostgreSQL 16 + PostGIS 3.4 | Relational + geospatial. Audit log append-only (DB trigger). |
| Maps     | OpenStreetMap raster tiles, Leaflet 1.9 | No Google Maps; OSM is free, attribution required. |
| Edge     | nginx 1.27-alpine (compose), host nginx for TLS | TLS terminated on host, plain HTTP into compose on `127.0.0.1:8081`. |
| CI/CD    | GitHub Actions, GHCR, Trivy | Build, test, coverage gate (≥75 %), SBOM image scan. |

## Why PostgreSQL + PostGIS over MongoDB

1. **Audit log integrity** – append-only requires transactional INSERTs with a DB-level trigger guard. Mongo's transaction story is workable but not native.
2. **Geospatial queries** – `ST_DWithin` over a GIST index is the canonical answer for "within N metres". Mongo's `$geoWithin` works but PostGIS is more powerful (KNN ordering via `<->` operator, geography vs geometry distinction).
3. **Schema stability** – the toilet entity has fixed shape; document flexibility buys nothing.
4. **One process to host** – one container, one backup pipeline. Less to harden.

## Domain shape

```mermaid
classDiagram
    class Toilet {
        +UUID id
        +String name
        +String address
        +Point location
        +String? pinCode
        +Boolean isWorking
        +ToiletType toiletType
        +String? notes
        +Long version
    }
    class AppUser {
        +UUID id
        +String username
        +String passwordHash
        +Role role
        +Boolean isEnabled
    }
    class AuditEntry {
        +Long id
        +OffsetDateTime occurredAt
        +UUID? actorId
        +String actorName
        +String? actorIp
        +String entityType
        +UUID? entityId
        +AuditOperation operation
        +String? fieldName
        +String? oldValue
        +String? newValue
        +UUID? requestId
    }
    class ToiletType { <<enumeration>> MCDONALDS GAS_STATION PARK CAFE PUBLIC OTHER }
    class Role { <<enumeration>> USER ADMIN }
    class AuditOperation { <<enumeration>> CREATE UPDATE DELETE LOGIN LOGIN_FAIL }

    Toilet --> ToiletType
    AppUser --> Role
    AuditEntry --> AuditOperation
```

## Use cases

```mermaid
graph LR
    U((matvs))
    UC1(Sign in)
    UC2(View list)
    UC3(View map)
    UC4(Toggle view)
    UC5(Search)
    UC6(Find near me)
    UC7(Create toilet)
    UC8(Edit toilet)
    UC9(Delete toilet)
    G((Geolocation))
    U --> UC1
    U --> UC2
    U --> UC3
    U --> UC4
    U --> UC5
    U --> UC6
    U --> UC7
    U --> UC8
    U --> UC9
    UC6 -.-> G
```

## Login sequence

```mermaid
sequenceDiagram
    autonumber
    actor M as matvs
    participant UI as LoginComponent
    participant Cli as AuthService (Angular)
    participant API as AuthController
    participant Svc as AuthService (Kotlin)
    participant BC as BCrypt
    participant Repo as UserRepository
    participant JWT as JwtService
    participant Audit as AuditService
    participant DB as Postgres
    M->>UI: tap toi-toi door
    UI->>UI: stage closed → opening → revealing → open
    M->>UI: submit(matvs, lap00p00, rememberMe=true)
    UI->>Cli: login(...)
    Cli->>API: POST /api/auth/login
    API->>Svc: login()
    Svc->>Repo: findByUsername(matvs)
    Repo->>DB: SELECT
    DB-->>Repo: row
    Repo-->>Svc: AppUser
    Svc->>BC: matches(raw, hash)
    BC-->>Svc: true
    Svc->>Audit: recordLogin(success=true)
    Audit->>DB: INSERT audit_entry
    Svc->>JWT: issue(matvs, ADMIN, true)
    JWT-->>Svc: TokenPair
    Svc-->>API: Success
    API-->>Cli: 200 TokenResponse
    Cli->>Cli: localStorage.setItem
    Cli-->>UI: ok
    UI->>UI: router.navigate(['/list'])
```

## Find-near-me sequence

```mermaid
sequenceDiagram
    autonumber
    actor M as matvs
    participant UI as ToiletsMapComponent
    participant Geo as Geolocation API
    participant API as ToiletController
    participant Svc as ToiletService
    participant DB as PostGIS
    M->>UI: click "find freepeepee close to me"
    UI->>Geo: getCurrentPosition()
    Geo-->>UI: (lat, lon)
    UI->>API: GET /api/toilets/nearby?lat&lon&radius=2000
    API->>Svc: nearby()
    Svc->>DB: SELECT ... WHERE ST_DWithin(location, point::geography, 2000) ORDER BY location <-> point
    DB-->>Svc: rows (sorted by distance)
    Svc-->>API: List<Toilet>
    API-->>UI: 200 List<ToiletDto>
    UI->>UI: render markers
```

## Activity – edit toilet (with audit diff)

```mermaid
flowchart TD
    A([User opens edit dialog]) --> B[Form pre-fills with current values + version]
    B --> C[User edits and submits]
    C --> D{Form valid?}
    D -- no --> E[Show inline validation] --> Z([end])
    D -- yes --> F[PUT /api/toilets/&#123;id&#125;]
    F --> G[JwtAuthFilter verifies token]
    G --> H{Entity exists?}
    H -- no --> I[404] --> Z
    H -- yes --> J{Version matches?}
    J -- no --> K[409 conflict] --> Z
    J -- yes --> L[Snapshot BEFORE]
    L --> M[Apply changes, JPA save]
    M --> N[Snapshot AFTER]
    N --> O[Diff via Kotlin reflection]
    O --> P[INSERT one AuditEntry per changed field]
    O --> Q[200 with updated DTO]
```

## Component view

```mermaid
flowchart LR
    U((User)) -->|HTTPS| HN[Host nginx<br/>TLS]
    HN -->|127.0.0.1:8081| EN[Edge nginx<br/>compose]
    EN -->|static| WEB[Angular SPA]
    EN -->|/api/**| API[Spring Boot]
    API --> DB[(PostgreSQL + PostGIS)]
    WEB -.tile.openstreetmap.org.-> OSM[(OSM tiles)]
    WEB --- SW[Service Worker]
```

## Deployment

```mermaid
flowchart TB
    subgraph Droplet["matvs.dev droplet"]
        subgraph HostN["Host nginx"]
            TLS["TLS (Let's Encrypt)"]
        end
        subgraph Docker["Docker compose"]
            E["edge (nginx)"]
            W["web (Angular static)"]
            A["api (Spring Boot 21)"]
            D[("db: postgis/postgis:16-3.4 + named vol pgdata")]
        end
    end
    UA[Browser / installed PWA] --> TLS --> E
    E --> W
    E --> A
    A --> D
```

## Login state machine

```mermaid
stateDiagram-v2
    [*] --> Closed: route /login
    Closed --> Opening: click door
    Opening --> Revealing: after 950 ms
    Revealing --> Handing: after 100 ms
    Handing --> Open: after 1450 ms
    Open --> Submitting: submit valid form
    Submitting --> Authenticated: 200
    Submitting --> Failed: 401 / error
    Failed --> Open: shake stops (600 ms)
    Authenticated --> [*]: nav /list
```

## Security model

- **Passwords** : BCrypt (cost 12). Hash only persisted; raw never logged. Rotate cost upward as hardware improves.
- **Sessions** : none. Stateless JWT (HS512, ≥64-byte secret, configured via env). Short-lived access token (30 min default), optional 14-day refresh issued only on `rememberMe=true`.
- **Transport** : TLS terminated by host nginx. Backend trusts `X-Forwarded-Proto`, `X-Forwarded-For`, `X-Request-Id` headers from the edge it sits behind.
- **Headers** : `X-Content-Type-Options nosniff`, `X-Frame-Options DENY`, CSP `default-src 'self'`, `Permissions-Policy geolocation=(self)`.
- **Audit log** : append-only at the DB level via trigger. Even an authenticated admin cannot rewrite history through the API.
- **CSRF** : N/A (no cookies; bearer token only).
- **Rate limit** : not implemented in this scaffold. Add at edge nginx (`limit_req_zone`) on `/api/auth/login` before production.

## Patterns at use

- **Sealed result types** (`AuthResult`, `ToiletOpResult`) – controllers map exhaustively, eliminating un-handled error paths at compile time.
- **Optimistic locking** on `Toilet.version` – clients pass the version they edited, server returns 409 on conflict.
- **Repository / Service / Controller** – classical layered separation; controllers are thin and contain no business logic.
- **Strategy via enum** – `ToiletType` is a closed set, persisted as a Postgres ENUM with Hibernate `NAMED_ENUM`.
- **Observer-ish audit** – every mutating service call records to the audit log inside the same transaction.
- **HTTP interceptors** (frontend) – `authInterceptor` attaches the bearer token; `errorInterceptor` reacts to 401 by signing out.
- **Standalone components + signals** (Angular 18) – no NgModules, zoneless-ready signals for state.
