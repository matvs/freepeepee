# freepeepee – runbook

This is the operational reference for running freepeepee on your matvs.dev droplet. It covers initial deploy, day-to-day operations, observability, and recovery scenarios.

## Architecture in 30 seconds

```
internet -> host nginx (TLS) -> 127.0.0.1:8081 -> edge nginx (compose) -> { web, api } -> db
```

Everything inside Docker. The host nginx is the only thing exposed publicly. The host nginx terminates TLS and forwards to the compose stack bound to localhost only.

## Prerequisites on the droplet

```
- Docker Engine >= 24
- docker compose plugin
- nginx, certbot (your existing setup for *.matvs.dev)
- git
- a /opt/freepeepee deployment dir owned by your deploy user
```

## First deploy

```bash
# 1. clone
sudo mkdir -p /opt/freepeepee
sudo chown $USER:$USER /opt/freepeepee
git clone <your-repo> /opt/freepeepee
cd /opt/freepeepee

# 2. .env (NOT committed)
cat > .env <<EOF
VERSION=v0.1.0
DB_USER=freepeepee
DB_PASSWORD=$(openssl rand -base64 32)
JWT_SECRET=$(openssl rand -base64 48)
CORS_ORIGINS=https://freepeepee.matvs.dev
EOF
chmod 600 .env

# 3. bring up the stack
docker compose pull   # uses GHCR images if you built via CI
docker compose up -d
docker compose ps

# 4. wire host nginx
sudo cp nginx/host-vhost.example.conf /etc/nginx/sites-available/freepeepee.matvs.dev
sudo ln -s /etc/nginx/sites-available/freepeepee.matvs.dev /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# 5. TLS
sudo certbot --nginx -d freepeepee.matvs.dev
```

You should be able to hit `https://freepeepee.matvs.dev` and see the toi-toi door. Tap, log in as `matvs` / `lap00p00`.

> Change the password immediately. The seed value exists because you specified it; treat it as bootstrap-only.

## Day-to-day commands

| Goal | Command |
|---|---|
| Show service status | `docker compose ps` |
| Tail API logs | `docker compose logs -f api` |
| Tail edge logs | `docker compose logs -f edge` |
| Restart API | `docker compose restart api` |
| Rebuild a single service | `docker compose build api && docker compose up -d api` |
| Update to a new image tag | `VERSION=v0.2.0 docker compose pull && docker compose up -d` |
| Open a psql shell | `docker compose exec db psql -U freepeepee` |
| Inspect audit log | `SELECT occurred_at, actor_name, operation, entity_type, field_name, old_value, new_value FROM audit_entry ORDER BY id DESC LIMIT 50;` |
| Show running migrations | `docker compose exec db psql -U freepeepee -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"` |

## Backup

The `db` container holds a named volume `pgdata`. Nightly pg_dump is recommended:

```bash
# /etc/cron.daily/freepeepee-backup
#!/bin/bash
set -euo pipefail
TS=$(date +%Y%m%d_%H%M%S)
DEST=/var/backups/freepeepee
mkdir -p "$DEST"
docker compose -f /opt/freepeepee/docker-compose.yml exec -T db \
  pg_dump -U freepeepee --no-owner --clean --if-exists freepeepee \
  | gzip > "$DEST/freepeepee_$TS.sql.gz"
find "$DEST" -type f -mtime +14 -delete
```

Test the backup occasionally :

```bash
gunzip -c /var/backups/freepeepee/<file>.sql.gz | head -50
```

## Restore

```bash
# stop the api so connections drain
docker compose stop api
# drop and recreate (DESTRUCTIVE)
gunzip -c <backup>.sql.gz | docker compose exec -T db psql -U freepeepee -d freepeepee
docker compose start api
```

## Rotating secrets

JWT secret rotation forces all sessions to expire (existing tokens fail verification). To rotate cleanly :

```bash
NEW=$(openssl rand -base64 48)
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$NEW|" /opt/freepeepee/.env
docker compose up -d api
```

DB password rotation requires updating both `.env` and the Postgres role :

```bash
docker compose exec db psql -U freepeepee -c "ALTER USER freepeepee WITH PASSWORD 'newpw';"
sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=newpw|" /opt/freepeepee/.env
docker compose up -d api
```

## Observability

- Liveness/readiness : `GET /actuator/health` (proxied at `/actuator/health` by edge nginx; public).
- Metrics : `GET /actuator/prometheus` (authenticated; consider scraping from a sidecar with a bearer token).
- Audit trail : `audit_entry` table; one row per CREATE / DELETE, one row per CHANGED FIELD on UPDATE, plus LOGIN / LOGIN_FAIL.
- HTTP request id : edge nginx generates `X-Request-Id` and the API copies it into audit rows, so a 4xx/5xx in nginx can be cross-referenced.

## Common failures

| Symptom | First check | Fix |
|---|---|---|
| 502 from host nginx | `docker compose ps` – is `edge` healthy? | `docker compose up -d edge` or check API container logs for a crash loop. |
| API container restarts on boot | `docker compose logs api` | usually Flyway failure or wrong `JWT_SECRET` length (must be ≥32 bytes for HS256). |
| `find near me` always denied | browser console | the user denied geolocation permission; site-settings reset is required in the browser. Also requires HTTPS; will not work over plain HTTP. |
| Map tiles do not load | network tab | OSM rate-limits aggressive use. If you exceed it, host your own tile server or switch to a CDN like MapTiler (free tier). |
| Login works, every subsequent request returns 401 | check JWT expiry vs system clock | NTP drift on the droplet; `timedatectl status`. |
| `409 version_conflict` on edit | two tabs editing the same row | reload the row, re-apply changes; this is the optimistic lock doing its job. |

## Upgrading the database

PostgreSQL major version upgrades require a dump/restore between images. Sketch :

```bash
docker compose exec -T db pg_dumpall -U postgres > /tmp/all.sql
# bump image in compose to postgis/postgis:17-3.5
docker compose down db
docker volume rm freepeepee_pgdata   # destructive
docker compose up -d db
sleep 10
docker compose exec -T db psql -U postgres < /tmp/all.sql
```

Always dump first, never the other way around.

## Disaster recovery

Worst case : droplet is gone.

1. New droplet, install Docker + nginx + certbot.
2. `git clone` the repo and create `/opt/freepeepee/.env` with the original `JWT_SECRET` and `DB_PASSWORD` (these you keep in 1Password or similar).
3. `docker compose up -d db` only, wait for healthy.
4. Restore the latest dump.
5. `docker compose up -d`.
6. Repoint DNS, re-issue cert.

Expected downtime : 15–30 min if you have backups and secrets handy.
