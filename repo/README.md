# Meridian Training Analytics Management System

**Project type:** fullstack

## Overview

Meridian is an on-premise training analytics and management platform that enables organisations to manage training courses, track learner progress, run assessment sessions, and generate compliance reports — all within a single self-hosted deployment.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Angular 17.3 (TypeScript) |
| Backend | Spring Boot 3.3 (Java 17) |
| Database | PostgreSQL 16 |
| Reverse proxy | nginx 1.25 (HTTPS on **:8443**, HTTP→HTTPS redirect on **:8080** by default in Docker) |
| Migrations | Flyway |
| Runtime | Docker Compose |

## Prerequisites

- Docker Desktop 24+ (or Docker Engine + Docker Compose v2)

That's all. No local Java, Node, Maven, `npm`/`pip`, or database installation is required.

> **No local runtime installs required.** Every command in this README — including the test commands — runs inside Docker. There is no `npm ci`, `mvn install`, or host toolchain step in any user-facing workflow. If a command asks you to install anything locally, that is a documentation bug; please open an issue.

## Quick Start

```bash
# 1. Enter the repo directory
cd repo

# 2. Copy environment template
cp .env.example .env

# 3. Start everything (builds images automatically on first run)
docker-compose up -d
# or (Compose v2 plugin syntax)
docker compose up -d
```

Docker Compose pulls/builds images as needed. The first run can take several minutes (Maven, Angular, images).

The UI is served over **HTTPS** (self-signed certificate) at **`https://localhost:8443/`** (see `MERIDIAN_HTTPS_PORT` in `.env`). HTTP on port 8080 redirects automatically to HTTPS. Your browser will show a one-time certificate warning for the self-signed cert — click "Advanced → Proceed" to continue.

> **URLs:** Open **`https://localhost:8443/`** (default). To use port 443, set `MERIDIAN_HTTPS_PORT=443` in `.env`. The HTTP port (`MERIDIAN_HTTP_PORT`, default `8080`) redirects to HTTPS and is not needed directly.

## Accessing the Application

| URL | Description |
|-----|-------------|
| `https://localhost:8443/` | Angular SPA (HTTPS, default) |
| `https://localhost:8443/api/v1/health` | Server health check |
| `https://localhost:8443/api/*` | REST API (proxied by nginx) |
| `http://localhost:8080/` | HTTP — redirects to HTTPS automatically |

## Verifying It Works

```bash
# 1. Health check — expects {"status":"UP","version":"0.0.1-SNAPSHOT"}
curl -sk https://localhost:8443/api/v1/health

# 2. Log in as admin and capture the access token
TOKEN=$(curl -sk -X POST https://localhost:8443/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123!","deviceFingerprint":"cli-verify"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 3. Fetch the authenticated user profile — expects JSON with "username":"admin"
curl -sk -H "Authorization: Bearer $TOKEN" https://localhost:8443/api/v1/users/me
```

> **Note:** `-k` skips TLS certificate verification for the self-signed local certificate. In production use a trusted certificate and remove `-k`.

**UI smoke check:**
1. Open `https://localhost:8443/` in your browser.
2. Accept the self-signed certificate warning (click "Advanced → Proceed to localhost").
3. Log in with `admin` / `Admin@123!`.
4. Confirm the dashboard loads and shows navigation items (Users, Sessions, Reports, Analytics, etc.).

## If you see “connection refused” (ERR_CONNECTION_REFUSED)

1. **Confirm the URL** is HTTPS with the correct port: **`https://localhost:8443/`** (default).
2. **Confirm Docker is running** and containers are up:
   ```bash
   docker compose ps
   ```
   You should see `repo-nginx-1` **Up** with **`0.0.0.0:8443->443/tcp`** (and `0.0.0.0:8080->80/tcp` for redirect).
3. **Start or restart the stack** from the `repo` directory:
   ```bash
   docker compose up -d
   ```
4. **Try IPv4 explicitly:** `https://127.0.0.1:8443/`
5. If port **8443** is already in use on your machine, pick another port in `.env`:
   ```bash
   echo 'MERIDIAN_HTTPS_PORT=9443' >> .env
   docker compose up -d
   ```
   Then open `https://localhost:9443/`.

## If `docker compose build` fails due network instability (ECONNRESET)

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

All test classes live natively in `server/src/test/java/com/meridian/` and `unit_tests/web/*.spec.ts`. **Every test command below runs 100% inside Docker** — no local Maven, Node, or `npm install` step is required. Dependencies are baked into the container images.

The one-shot runner executes backend, web, and (optionally) E2E suites in Docker:

```bash
# All backend + web suites (Docker only — no host runtime needed)
./run_tests.sh

# Include Playwright E2E (requires docker compose up -d first)
./run_tests.sh --e2e
```

Or run individual suites directly (all Docker-contained):

```bash
# Server unit tests + API integration tests — runs in the server image (Java 17 + Maven baked in)
docker compose run --rm server ./mvnw test --no-transfer-progress

# Web unit tests — dependencies are baked into the web-test image by the `test`
# stage of web/Dockerfile, so there is no `npm install` step at run time.
docker compose --profile test run --rm web-test

# E2E tests — requires the full stack to be running first (docker compose up -d)
# API_URL defaults to https://localhost:8443 (Playwright accepts the self-signed cert).
# The Playwright image ships with every runtime dependency already installed.
#
# Linux — --network host reaches the local nginx directly:
docker run --rm --network host \
  -v "$(pwd)/e2e_tests:/app" -w /app \
  mcr.microsoft.com/playwright:v1.44.0-jammy \
  npx playwright test

# macOS / Windows — Docker Desktop does NOT expose host ports via --network host;
# reach the host over the docker bridge (host.docker.internal) instead:
docker run --rm \
  -e API_URL=https://host.docker.internal:8443 \
  -v "$(pwd)/e2e_tests:/app" -w /app \
  mcr.microsoft.com/playwright:v1.44.0-jammy \
  npx playwright test
```

> **Environment rule compliance:** No user-facing test workflow requires `npm ci`, `npm install`, `pip install`, `mvn install`, or any local toolchain. Every command boots a prebuilt image.

## Project Structure

```
repo/
├── server/           Spring Boot API (Java 17, Maven)
├── web/              Angular SPA (TypeScript)
├── nginx/            Reverse-proxy config (HTTPS with self-signed cert for local dev)
├── scripts/          Utility shell scripts
├── api_tests/        Archival mirror of API integration tests (authoritative copies live in server/src/test/java)
├── unit_tests/       Archival mirror of backend + frontend unit tests
├── e2e_tests/        Playwright end-to-end tests
├── docs-generated/   Generated API docs (OpenAPI / swagger-ui output)
├── docker-compose.yml
├── .env.example
└── run_tests.sh      Local test-runner convenience script
```

```
host :8080 (HTTP)  → nginx :80  → 301 redirect to https://localhost:8443/
host :8443 (HTTPS) → nginx :443
 ├── /api/*  → server (Spring Boot :8080 inside the network)
 └── /*      → web (Angular static nginx :80)
```

## Configuration

Copy `.env.example` to `.env` and edit as needed. All variables have sensible defaults for local development.

| Variable | Description | Default |
|----------|-------------|---------|
| `MERIDIAN_HTTP_PORT` | Host port for HTTP→HTTPS redirect | `8080` |
| `MERIDIAN_HTTPS_PORT` | Host port for HTTPS (self-signed TLS) | `8443` |
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

1. Generate a new key: `docker run --rm alpine/openssl rand -base64 32`
2. Write a one-time migration to re-encrypt existing rows with the new key.
3. Deploy the migration, then update `AES_KEY_BASE64` and restart the server.

There is no automatic re-encryption on startup — rotation is intentionally manual to avoid data loss on misconfiguration.

## Known Limitations

- The local Docker setup uses a **self-signed TLS certificate** (generated at image build time). Browsers will show a one-time warning; click "Advanced → Proceed" to continue. Production deployments should replace this with a certificate from a trusted CA.
- API integration tests include a broad `TrueNoMockHttpApiTest` suite that boots the real Spring application on a random TCP port and drives it with `TestRestTemplate` (real sockets, real filter chain, real JWTs via the login endpoint). Legacy suites additionally use `MockMvc` for fast role-matrix coverage; those suites still authenticate through the real `JwtAuthenticationFilter` via `TestAuthHelper`. All test classes live in `server/src/test/java/com/meridian/` and are run in Docker by `./run_tests.sh` (no host Maven/Java required).
- E2E tests default `API_URL` to `https://localhost:8443`; align that with your `MERIDIAN_HTTPS_PORT` or set `API_URL` when the API is only reachable via the nginx proxy port.
- Backup and recovery-drill features invoke `pg_dump`/`pg_restore` inside the server container; no external backup storage is wired in the default setup.
