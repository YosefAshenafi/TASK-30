# Meridian Training Analytics Management System

**Project type:** fullstack

## Overview

Meridian is an on-premise training analytics and management platform that enables organisations to manage training courses, track learner progress, run assessment sessions, and generate compliance reports — all within a single, self-hosted deployment.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Angular 18 (TypeScript) |
| Backend | Spring Boot 3.3 (Java 17) |
| Database | PostgreSQL 16 |
| Reverse proxy | nginx 1.25 (HTTPS :443) |
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

# 3. Build and start all services (the nginx image includes a dev self-signed certificate)
docker compose up --build -d
```

Services take 2–5 minutes on first build (Maven compiles the server; Angular bundles the SPA).

## Accessing the Application

| URL | Description |
|-----|-------------|
| `https://localhost/` | Angular SPA |
| `https://localhost/api/v1/health` | Server health check |
| `https://localhost/api/*` | REST API (proxied by nginx) |

> The TLS certificate is self-signed. Accept the browser security warning, or pass `-k` to curl.

## Verifying It Works

```bash
# 1. Health check — expects {"status":"UP","version":"0.0.1-SNAPSHOT"}
curl -ks https://localhost/api/v1/health

# 2. Log in as admin and capture the access token
TOKEN=$(curl -ks -X POST https://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123!","deviceFingerprint":"cli-verify"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 3. Fetch the authenticated user profile — expects JSON with "username":"admin"
curl -ks -H "Authorization: Bearer $TOKEN" https://localhost/api/v1/users/me
```

**UI smoke check:**
1. Open `https://localhost/` in your browser and accept the self-signed certificate warning.
2. Log in with `admin` / `Admin@123!`.
3. Confirm the dashboard loads and shows navigation items (Users, Sessions, Reports, Analytics, etc.).

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
├── nginx/            Reverse-proxy config (TLS baked into image)
├── scripts/          Utility shell scripts
├── api_tests/        MockMvc-based API integration tests
├── unit_tests/       Backend (JUnit) and frontend (Jasmine) unit tests
├── e2e_tests/        Playwright end-to-end tests
├── docker-compose.yml
├── .env.example
└── run_tests.sh      Local test-runner convenience script
```

```
nginx (HTTPS :443)
 ├── /api/*  → server (Spring Boot :8080)
 └── /*      → web (Angular via nginx :80)
```

## Configuration

Copy `.env.example` to `.env` and edit as needed. All variables have sensible defaults for local development.

| Variable | Description | Default |
|----------|-------------|---------|
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
| `docker compose up --build -d` | Build images and start all services |
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

- TLS certificate is self-signed; browsers will show a security warning. Accept it or import the cert into your trust store.
- API integration tests use `MockMvc` (HTTP-like, not real network transport) and mock the security principal via `@WithMockUser` in several suites.
- E2E tests hit the live stack at `http://localhost:8080` and require `docker compose up` to be running.
- Backup and recovery-drill features invoke `pg_dump`/`pg_restore` inside the server container; no external backup storage is wired in the default setup.
