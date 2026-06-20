<img src="docs/assets/logo.svg" alt="freepeepee" width="380"/>

# freepeepee

> find a loo, fast. zurich-first. wherever there's plumbing.

A login-gated full-stack PWA for cataloguing public toilets : list **or** map view, free-text search, "find one close to me" via geolocation, optimistic-locked CRUD with field-level audit log.

## Tech

- **Backend** : Spring Boot 3.3 in Kotlin 1.9 (functional / sealed-result style), Spring Security, Hibernate Spatial 6.5, Flyway 10, JJWT.
- **Frontend** : Angular 18 (standalone, signals) + Angular Material + Leaflet (OpenStreetMap) + SCSS + Pug (via `@webdiscus/pug-loader`) + Service Worker (installable PWA).
- **Database** : PostgreSQL 16 + PostGIS 3.4.
- **Build / CI** : Gradle Kotlin DSL, npm, GitHub Actions (test → coverage gate → GHCR push → Trivy).
- **Runtime** : Docker Compose, nginx at the edge, your existing host nginx for TLS.

## Repository layout

```
freepeepee/
├── backend/                 Spring Boot 3 + Kotlin
│   ├── src/main/kotlin/...  domain, repository, service, web, security, config
│   ├── src/main/resources/  application.yml, Flyway migrations
│   └── src/test/kotlin/...  Kotest + Testcontainers
├── frontend/                Angular 18
│   ├── src/app/             features (login, shell, toilets-list, toilets-map, toilet-form), core (auth, http, guards, services), shared (models)
│   ├── webpack.config.js    pug-loader injection
│   └── ngsw-config.json     service worker
├── docs/
│   ├── architecture.md      stack rationale + 9 Mermaid diagrams that render on GitHub
│   ├── runbook.md           operate, observe, recover
│   ├── diagrams/*.puml      same 9 diagrams in PlantUML source
│   └── assets/              logo, favicon
├── nginx/
│   ├── freepeepee.conf      edge nginx (lives inside compose)
│   └── host-vhost.example.conf   drop on your host nginx
├── docker-compose.yml
└── .github/workflows/       backend-ci, frontend-ci, docker-release, deploy
```

## Quickstart (local)

```bash
# 1. Postgres + PostGIS
docker run --name pg-fp -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=freepeepee -e POSTGRES_USER=freepeepee -p 5432:5432 -d postgis/postgis:16-3.4

# 2. backend
cd backend
DB_PASSWORD=changeme JWT_SECRET=$(openssl rand -base64 48) ADMIN_USERNAME=admin ADMIN_PASSWORD=changeme ./gradlew bootRun

# 3. frontend
cd ../frontend
npm i
npm start    # http://localhost:4200
```

Open http://localhost:4200 — the catalogue is **public and read-only**. To edit, go to
`http://localhost:4200/admin` and sign in with the `ADMIN_USERNAME` / `ADMIN_PASSWORD` you set
in your environment. There is intentionally no link to `/admin` in the UI.

## Quickstart (Docker)

```bash
cp .env.example .env   # then fill in DB_PASSWORD, JWT_SECRET, ADMIN_USERNAME, ADMIN_PASSWORD, VERSION
docker compose up -d --build
# open http://localhost:8081
```

## Test

```bash
cd backend && ./gradlew test         # Kotest + Testcontainers (real Postgres+PostGIS)
cd frontend && npm test               # Karma + Jasmine
```

Coverage gate enforced at 75 % in `backend/build.gradle.kts` via Jacoco. CI uploads the HTML reports as workflow artifacts.

## Deploy

See [`docs/runbook.md`](docs/runbook.md). Short version : tag `v0.1.0`, GitHub Actions builds images to GHCR, `docker compose pull && up -d` on the droplet, host nginx + certbot do TLS.

## What this scaffold is and isn't

**Is** : production-shaped code with the security boundary, audit log, optimistic locking, PostGIS geospatial query, and PWA wiring already correct. Tests exist for the highest-risk paths (auth flow end-to-end, audit diff, CRUD over real PostGIS).

**Isn't** : exhaustively tested. The login animation is opinionated and untested in CI; the search index is naive `to_tsvector('simple', …)` without language stemming; there's no rate limiter on `/api/auth/login`. These are intentional gaps – fix in priority order.

## License

Personal project. Not licensed for redistribution yet.
