# Meridian Training Analytics Management System

**Project type:** fullstack

## Overview

Meridian is an on-premise training analytics and management platform that enables organisations to manage training courses, track learner progress, run assessment sessions, and generate compliance reports — all within a single self-hosted deployment.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Angular 18 (TypeScript) |
| Backend | Spring Boot 3.3 (Java 17) |
| Database | PostgreSQL 16 |
| Reverse proxy | nginx 1.25 (HTTP; default **:8080** on the host in Docker) |
| Migrations | Flyway |
| Runtime | Docker Compose |

## Prerequisites

- Docker Desktop 24+ (or Docker Engine + Docker Compose v2)

That's all. No local Java, Node, or database installation is required.

## Quick Start

```bash
# 1. Enter the repo directory
cd repo

# 2. Copy environment template
cp .env.example .env

# 3. Start everything (builds images automatically on first run)
docker compose up -d
```

Docker Compose pulls/builds images as needed. The first run can take several minutes (Maven, Angular, images).

The UI is served on **plain HTTP** at **`http://localhost:8080/`** (see `MERIDIAN_HTTP_PORT` in `.env`). No browser TLS warnings. Use HTTPS only in production behind a real certificate.

> **URLs:** Open **`http://localhost:8080/`** — not `https://` (nothing listens on 443). If you use port **80** instead, set `MERIDIAN_HTTP_PORT=80` in `.env` and use `http://localhost/`.

## Accessing the Application

| URL | Description |
|-----|-------------|
| `http://localhost:8080/` | Angular SPA (default) |
| `http://localhost:8080/api/v1/health` | Server health check |
| `http://localhost:8080/api/*` | REST API (proxied by nginx) |

## Verifying It Works

```bash
# 1. Health check — expects {"status":"UP","version":"0.0.1-SNAPSHOT"}
curl -s http://localhost:8080/api/v1/health

# 2. Log in as admin and capture the access token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123!","deviceFingerprint":"cli-verify"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 3. Fetch the authenticated user profile — expects JSON with "username":"admin"
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/users/me
```

**UI smoke check:**
1. Open `http://localhost:8080/` in your browser.
2. Log in with `admin` / `Admin@123!`.
3. Confirm the dashboard loads and shows navigation items (Users, Sessions, Reports, Analytics, etc.).

## If you see “connection refused” (ERR_CONNECTION_REFUSED)

1. **Confirm the URL** includes the port: **`http://localhost:8080/`** (default), not `https://` and not plain `http://localhost/` unless you set `MERIDIAN_HTTP_PORT=80`.
2. **Confirm Docker is running** and containers are up:
   ```bash
   docker compose ps
   ```
   You should see `repo-nginx-1` **Up** with **`0.0.0.0:8080->80/tcp`** (or your chosen port).
3. **Start or restart the stack** from the `repo` directory:
   ```bash
   docker compose up -d
   ```
4. **Try IPv4 explicitly:** `http://127.0.0.1:8080/`
5. If port **8080** is already in use on your machine, pick another port in `.env`:
   ```bash
   echo 'MERIDIAN_HTTP_PORT=9080' >> .env
   docker compose up -d
   ```
   Then open `http://localhost:9080/`.

## If `docker compose build` fails on `npm ci` (ECONNRESET / network)

The web image enables **longer npm timeouts and retries**, plus **one automatic retry** after a short pause. If the registry download still fails (VPN, proxy, or unstable Wi‑Fi), run the build again when the network is stable, or build only the web service after fixing connectivity:

```bash
docker compose build web
```

Ensure `web/.dockerignore` is present so the build context does not include `node_modules` or `coverage` (huge tar uploads can also cause failures).

## Demo Credentials

All accounts are seeded automatically on first boot via Flyway migrations.

| Username | Password | Role |
|----------|----------|------|
| `admin` | `Admin@123!` | Administrator |
| `student1` | `Test@123!` | Student |
| `student2` | `Test@123!` | Student |
| `mentor1` | `Test@123!` | Corporate Mentor |
| `faculty1` | `Test@123!` | Faculty Mentor |

## Running the Tests

All primary test commands run inside Docker — no local Maven or Node installation required.

```bash
# Server unit tests + API integration tests (runs inside the server dev container)
docker compose run --rm server ./mvnw test --no-transfer-progress

# Web unit tests
docker run --rm \
  -v "$(pwd)/web:/app" -w /app \
  node:20-alpine sh -c \
  "npm ci --legacy-peer-deps --silent && npm test -- --watch=false --browsers=ChromeHeadlessCI"

# E2E tests — requires the full stack to be running first (docker compose up -d)
docker run --rm \
  -v "$(pwd)/e2e_tests:/app" -w /app \
  --network host \
  mcr.microsoft.com/playwright:v1.44.0-jammy sh -c \
  "npm ci --silent && npx playwright test"
```

> **Convenience script** — If you have Java 17 and Node 20 installed locally, `./run_tests.sh` runs all suites and prints a summary table. Pass `--e2e` to include Playwright tests against a running stack.

## Project Structure

```
repo/
├── server/           Spring Boot API (Java 17, Maven)
├── web/              Angular SPA (TypeScript)
├── nginx/            Reverse-proxy config (HTTP for local dev)
├── scripts/          Utility shell scripts
├── api_tests/        MockMvc-based API integration tests
├── unit_tests/       Backend (JUnit) and frontend (Jasmine) unit tests
├── e2e_tests/        Playwright end-to-end tests
├── docker-compose.yml
├── .env.example
└── run_tests.sh      Local test-runner convenience script
```

```
host :8080 (default) → nginx :80
 ├── /api/*  → server (Spring Boot :8080 inside the network)
 └── /*      → web (Angular static nginx :80)
```

## Configuration

Copy `.env.example` to `.env` and edit as needed. All variables have sensible defaults for local development.

| Variable | Description | Default |
|----------|-------------|---------|
| `MERIDIAN_HTTP_PORT` | Host port mapped to nginx (UI + `/api`) | `8080` |
| `DB_NAME` | PostgreSQL database name | `meridian` |
| `DB_USERNAME` | PostgreSQL user | `meridian` |
| `DB_PASSWORD` | PostgreSQL password | `meridian_secret` |
| `JWT_SIGNING_KEY` | HMAC-SHA256 signing key (≥ 32 chars) | *(dev default set)* |
| `AES_KEY_BASE64` | Base64-encoded 32-byte AES-256 key | *(dev default set)* |
| `BACKUP_PATH` | Backup storage path inside server container | `/app/backups` |
| `EXPORT_PATH` | Report export path inside server container | `/app/exports` |
| `EXPORTS_ADMIN_APPROVAL_REQUIRED` | Require approval workflow for admin exports | `true` |
| `RECYCLE_BIN_RETENTION_DAYS` | Days before hard-delete from recycle bin | `14` |
| `REGISTRATION_SLA_BUSINESS_DAYS` | Business days before registration SLA escalation | `2` |

## Common Commands

| Command | Description |
|---------|-------------|
| `docker compose up -d` | Build (if needed) and start all services |
| `docker compose down` | Stop and remove containers |
| `docker compose logs -f` | Tail all service logs |
| `docker compose exec postgres psql -U meridian meridian` | Open psql on the database |
| `docker compose exec server ./mvnw flyway:migrate` | Run Flyway migrations manually |

## Encryption

Sensitive database columns are encrypted at rest with **AES-256-GCM** via `AesAttributeConverter`. Each value is stored with a fresh 12-byte random IV prepended, then base64-encoded.

### Key rotation

1. Generate a new key: `docker run --rm alpine sh -c "apk add -q openssl && openssl rand -base64 32"`
2. Write a one-time migration to re-encrypt existing rows with the new key.
3. Deploy the migration, then update `AES_KEY_BASE64` and restart the server.

There is no automatic re-encryption on startup — rotation is intentionally manual to avoid data loss on misconfiguration.

## Known Limitations

- The default Docker setup uses **HTTP** for convenience. Production deployments should terminate **HTTPS** at a load balancer or reverse proxy with a trusted certificate.
- API integration tests use `MockMvc` (HTTP-like, not real network transport) and mock the security principal via `@WithMockUser` in several suites.
- E2E tests default `API_URL` to `http://localhost:8080`; align that with your `MERIDIAN_HTTP_PORT` or set `API_URL` when the API is only reachable via the nginx proxy port.
- Backup and recovery-drill features invoke `pg_dump`/`pg_restore` inside the server container; no external backup storage is wired in the default setup.
