# Meridian Training Analytics Management System — Design Document

## 1. Overview

Meridian is an on-premise, offline-capable training analytics platform. An Angular 17 SPA serves four roles (Student, Corporate Mentor, Faculty Mentor, Administrator) and communicates exclusively through a decoupled Spring Boot 3.2 REST API backed by PostgreSQL 15. The entire stack runs inside Docker containers; no host-local tooling is required. All secrets are generated at container start via `entrypoint.sh`; no `.env` files exist.

---

## 2. Architecture & Component Map

### 2.1 High-Level Topology

```
Browser (Angular 17 SPA)
│
│  HTTP/HTTPS  (proxied through Nginx :4200 → backend :8080)
│
├── Nginx reverse proxy  (:4200)
│       └── /api/* → http://backend:8080/api/*
│       └── /* → Angular static bundle (index.html fallback)
│
Spring Boot REST API  (:8080)
│
├── Spring Security filter chain (JWT validation, CORS, security headers)
├── Controllers (pkg: com.meridian.controller)
├── Services    (pkg: com.meridian.service)
├── Repositories (Spring Data JPA — pkg: com.meridian.repository)
├── Quartz Scheduler (JDBC job store — PostgreSQL)
└── Flyway (schema migration — V1…Vn)
│
PostgreSQL 15  (:5432)
│
└── 22 tables (see §3)

Client offline layer
└── IndexedDB (Dexie.js)  — session logs + draft assessments
        └── SyncService → POST /api/sessions/sync (idempotency key)
```

### 2.2 Angular Module ↔ Spring Controller ↔ DB Table Map

| Angular Feature Module | Spring Controller | Primary DB Tables |
|---|---|---|
| `AuthModule` | `AuthController` | `users`, `refresh_tokens` |
| `AdminModule` (user mgmt) | `UserManagementController` | `users`, `user_roles`, `organizations` |
| `SessionModule` | `SessionController`, `SessionSyncController` | `training_sessions`, `session_activities` |
| `AnalyticsModule` | `AnalyticsController` | `attempts`, `assessment_items`, `courses` |
| `ReportingModule` | `ReportController`, `ReportScheduleController`, `ReportExportController` | `report_schedules`, `training_sessions`, `attempts` |
| `NotificationModule` | `NotificationController` | `notifications`, `notification_templates` |
| `GovernanceModule` | `GovernanceController` | `data_permissions`, `users` |
| `AuditModule` | `AuditController` | `audit_events` |
| `AnomalyModule` | `AnomalyController` | `anomalies`, `device_fingerprints`, `security_policies` |
| `ApprovalModule` | `ApprovalController` | `approvals` |
| `BackupModule` | `BackupController` | `backups`, `security_policies` |
| `RecycleBinModule` | `RecycleBinController` | `recycle_bin` |

---

## 3. Database Schema

### 3.1 Table Inventory (22 tables)

| Table | Purpose |
|---|---|
| `organizations` | Tenant records; Corporate Mentors are scoped per org |
| `users` | All accounts; `status` ∈ {PENDING, ACTIVE, LOCKED, REJECTED}; soft-delete via `deleted_at` |
| `roles` | 4 roles: ROLE_STUDENT, ROLE_CORPORATE_MENTOR, ROLE_FACULTY_MENTOR, ROLE_ADMINISTRATOR |
| `user_roles` | Many-to-many join |
| `refresh_tokens` | Server-side JWT refresh token allow-list; `revoked_at` enables immediate invalidation |
| `courses` | Course catalog with version, location, instructor; soft-delete |
| `assessment_items` | Questions per course; stores `difficulty` and `discrimination` metrics |
| `training_sessions` | Per-student session; `status` ∈ {IN_PROGRESS, COMPLETED, INTERRUPTED}; `idempotency_key` for sync dedup |
| `session_activities` | Per-activity check-off rows within a session |
| `attempts` | Assessment answer records; drives all analytics |
| `notification_templates` | Editable subject/body templates by type |
| `notifications` | Per-user delivered notifications; `read_at` tracking |
| `report_schedules` | Quartz-backed export jobs (CSV/PDF, cron expression, output path) |
| `data_permissions` | Per-user field-level unmasking grants |
| `audit_events` | Immutable event log: LOGIN_SUCCESS/FAILURE, EXPORT, PERMISSION_CHANGE, DATA_DELETE, DATA_ACCESS |
| `device_fingerprints` | Per-user SHA-256 hash of (User-Agent, Accept-Language, timezone-offset); up to 5 per user |
| `anomalies` | Recorded anomaly events (new device, IP outside range, export burst) |
| `approvals` | Workflow records for permission changes and export requests; `status` ∈ {PENDING, APPROVED, REJECTED} |
| `backups` | Backup run history with path, size, retention deadline |
| `recycle_bin` | Soft-deleted entity snapshots (JSONB); expires after 14 days via Quartz purge job |
| `security_policies` | Key-value config store: CIDR ranges, enforcement mode, rate limits, backup settings |

### 3.2 Multi-Tenancy

Row-level security (PostgreSQL RLS) is applied on all organization-scoped tables (`training_sessions`, `attempts`, `report_schedules`, `notifications`). Spring Security `@PreAuthorize` expressions assert `principal.organizationId == resource.organizationId` as an application-layer backstop. This dual-layer prevents cross-tenant leakage even if a service-layer check is misconfigured.

### 3.3 Data Classification

Fields are tagged with classification tiers: `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`. Sensitive fields (employee IDs, contact details) are masked by default using a `@FieldMask` annotation on JPA projections; the full value is returned only when the requesting user has a matching row in `data_permissions`. AES-256-GCM encryption (JCA) is applied at-rest to columns classified CONFIDENTIAL or RESTRICTED before storage.

---

## 4. Data Flow Diagrams

### 4.1 Authentication Flow

```
Client                          Backend                         DB
  │                                │                             │
  │── POST /api/auth/login ────────▶                             │
  │   {username, password}         │── bcrypt.verify ───────────▶ users
  │                                │◀── user row ────────────────│
  │                                │── check status == ACTIVE    │
  │                                │── check locked_until        │
  │                                │                             │
  │                                │── generate access JWT (15m) │
  │                                │── generate refresh JWT (7d) │
  │                                │── hash(refreshToken) ───────▶ refresh_tokens
  │                                │── write audit LOGIN_SUCCESS ▶ audit_events
  │                                │                             │
  │◀── 200 {accessToken}           │                             │
  │    Set-Cookie: refresh (HttpOnly, Secure)                    │
  │                                │                             │
  │── GET /api/... ────────────────▶                             │
  │   Authorization: Bearer <AT>   │── JwtFilter validates AT    │
  │                                │── SecurityContext populated │
  │◀── 200 response ───────────────│                             │

Token refresh:
  │── POST /api/auth/refresh ──────▶                             │
  │   Cookie: refresh=<RT>         │── hash(RT) lookup ──────────▶ refresh_tokens
  │                                │── revoke old token          │
  │                                │── issue new AT + RT         │
  │◀── 200 {newAccessToken}        │                             │
```

### 4.2 Offline Session Capture & Sync Flow

```
Student Browser (online)
  ├── POST /api/sessions → session created server-side + IndexedDB
  ├── PUT /api/sessions/{id} → activity check-offs synced in real-time
  └── POST /api/sessions/{id}/complete → finalized

Student Browser (offline)
  ├── Writes to IndexedDB (Dexie.js):
  │     { idempotencyKey: UUID v4, syncStatus: PENDING, payload: {...} }
  ├── In-session timer runs from local state
  └── UI shows "Saved locally" banner

On LAN Reconnect (SyncService)
  ├── Reads all IndexedDB records with syncStatus == PENDING
  ├── Batches ≤ 500 records per request
  ├── POST /api/sessions/sync → backend processes batch
  │     ├── Checks idempotency_key uniqueness (dedup)
  │     ├── Compares course_version_id
  │     │     ├── Match → persist, mark SYNCED
  │     │     └── Mismatch → persist with sync_status=SYNC_VERSION_MISMATCH
  │     │                     notify instructor in-app
  │     └── Returns per-record status
  └── IndexedDB records updated: PENDING → SYNCED | CONFLICT
```

### 4.3 In-App Notification Flow

```
Backend event (anomaly, approval, export complete, account status change)
  │
  ├── NotificationService.create(userId, type, subject, body)
  │     └── INSERT INTO notifications
  │
  └── SSE endpoint: GET /api/notifications/stream
        └── Angular EventSource → pushes new notification events
              └── Client-side polling fallback every 30s if SSE unavailable
```

### 4.4 Backup Flow

```
Quartz Scheduler (JDBC job store in PostgreSQL)
  │
  ├── NightlyBackupJob (incremental, cron: 0 2 * * *)
  │     └── pg_dump --format=custom --exclude-table-data=audit_events
  │
  └── WeeklyBackupJob (full, cron: 0 1 * * 0)
        └── pg_dump --format=custom (all tables)

Both jobs:
  ├── Encrypt output with AES-256-GCM (DEK per backup, KEK from admin passphrase via PBKDF2)
  ├── Write to admin-configured path (security_policies.backup_path)
  ├── INSERT INTO backups (type, path, size_bytes, retention_until = now + 30d)
  └── Purge expired: DELETE FROM backups WHERE retention_until < now

RecycleBinPurgeJob (nightly)
  └── DELETE FROM recycle_bin WHERE expires_at < now → audit event DATA_DELETE
```

### 4.5 Anomaly Detection Flow

```
Every authenticated request → AnomalyDetectionFilter
  │
  ├── Compute fingerprint = SHA-256(User-Agent + Accept-Language + timezone-offset)
  ├── Lookup device_fingerprints WHERE user_id = ? AND fingerprint_hash = ?
  │     └── Not found → INSERT + fire ANOMALY_ALERT notification (new device)
  │
  ├── Check request IP against security_policies.allowed_cidr_ranges
  │     └── Outside range → log anomaly; if enforcement_mode=BLOCK → 403
  │
  └── Export rate check (sliding 10-min window on audit_events)
        └── > 20 EXPORT events in 10 min → fire ANOMALY_ALERT
```

---

## 5. Key Technical Decisions

### 5.1 JWT Token Strategy
- **Access token**: 15-minute TTL, stateless, carries `userId`, `roles`, `organizationId`.
- **Refresh token**: 7-day TTL, stored as `bcrypt(token)` hash in `refresh_tokens`; rotation on every use (old hash revoked, new hash inserted). This allows immediate per-user revocation on logout or anomaly detection without global token invalidation.
- **Why not opaque session tokens**: The offline-capable architecture requires the Angular client to self-issue access tokens from the refresh cookie without a round-trip login; stateless JWTs enable this.

### 5.2 AES Key Model
- **Application data**: AES-256-GCM via JCA; key sourced from `ENCRYPTION_KEY` environment variable (auto-generated by `entrypoint.sh` if not set). IV is stored prepended to ciphertext.
- **Backup files**: Two-key model — a per-backup DEK, itself encrypted with a KEK derived from an administrator passphrase via PBKDF2 (100,000 iterations, SHA-256). Encrypted DEK stored alongside backup file. Passphrase rotation re-encrypts only DEKs, not backup content.
- **Passwords**: BCrypt (strength 12) — never AES; one-way only.

### 5.3 Quartz Job Store
Quartz uses `JobStoreTX` backed by the same PostgreSQL instance rather than `@Scheduled` annotations. This means:
- Export schedule configurations survive application restarts.
- Cluster-safe locking allows a standby server promotion without duplicate job execution.
- Schedules are managed via the Admin UI (`POST /api/reports/schedules`) without SSH access.

### 5.4 Offline Sync — Last-Write-Wins
Sync uses LWW at the session-log level (not course-structure level). The `course_version_id` is captured at session start inside the IndexedDB payload. On sync, a version mismatch does not discard the offline session but flags it `SYNC_VERSION_MISMATCH` and notifies the instructor. This prevents silent data loss while keeping the sync path non-interactive.

### 5.5 Multi-Tenant Isolation Strategy
PostgreSQL RLS policies on organization-scoped tables act as a database-level backstop. Spring Security `@PreAuthorize` checks at the service layer act as the primary enforcement point. Single-schema multi-tenant is sufficient for the on-premise scale (≤500 concurrent users, ≤50,000 learner records); separate schemas would add operational complexity with no benefit.

### 5.6 Device Fingerprinting
SHA-256 of `(User-Agent, Accept-Language, timezone-offset)` is computed server-side on every login. No external fingerprinting library is required (safe for air-gapped on-premise deployment). Each user maintains an allow-list of up to 5 known fingerprints. A new fingerprint triggers an in-app anomaly notification; the user acknowledges to add it to the allow-list.

### 5.7 Notification Delivery
Server-sent events (SSE) at `GET /api/notifications/stream` are the primary delivery channel. SSE fits the single-server on-premise topology (no broker required); Angular `EventSource` handles reconnection. All notifications are persisted to the `notifications` table regardless of delivery success, with a 30-second polling fallback for clients that cannot maintain SSE connections.

### 5.8 Sizing Assumptions
| Parameter | Value | Config property |
|---|---|---|
| Max concurrent users | 500 | — |
| Total learner records | 50,000 | — |
| Assessment attempt rows | 5 million | — |
| HikariCP max pool | 20 | `spring.datasource.hikari.maximum-pool-size` |
| HikariCP min idle | 5 | `spring.datasource.hikari.minimum-idle` |
| Sync batch size | 500 records | `app.sync.batch-size` |

---

## 6. Security Architecture

| Control | Implementation |
|---|---|
| Transport | HTTPS enforced via HSTS header (`max-age=31536000; includeSubDomains`); TLS terminated at Nginx |
| Security headers | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'self'`, `Referrer-Policy: strict-origin-when-cross-origin` |
| Authentication | JWT (jjwt 0.12); HttpOnly + Secure refresh cookie |
| Password storage | BCrypt (strength 12) |
| At-rest encryption | AES-256-GCM via JCA for CONFIDENTIAL/RESTRICTED fields |
| Account lockout | 15-minute lock after 5 consecutive failures (`users.locked_until`) |
| Rate limiting | Configurable per-endpoint/role via `security_policies` |
| Audit logging | Immutable `audit_events` table; captures LOGIN, EXPORT, PERMISSION_CHANGE, DATA_DELETE |
| Anomaly detection | New device fingerprint, out-of-range IP, export burst (>20 in 10 min) |
| Object-level authz | Spring Security `@PreAuthorize` + PostgreSQL RLS |
| Approval workflows | `approvals` table for permission changes and export requests above threshold |

---

## 7. Trade-offs

| Decision | Chosen | Alternative | Reason for choice |
|---|---|---|---|
| Refresh token storage | Server-side hash allow-list | Stateless refresh JWTs | Enables immediate revocation on logout/anomaly |
| Sync conflict resolution | LWW + version-mismatch flag | Interactive conflict UI | Non-interactive sync path; instructors review flagged sessions |
| Multi-tenant isolation | RLS + app-layer `@PreAuthorize` | Separate schemas per org | Lower operational overhead at on-premise scale |
| Device fingerprinting | Server-side SHA-256 hash | FingerprintJS library | No external dependency; safe for air-gapped environments |
| Notification delivery | SSE + persistence | WebSocket | No broker required; simpler on single-server topology |
| Backup encryption key | Admin passphrase + PBKDF2 DEK/KEK | OS keychain | Passphrase rotation without re-encrypting backup content |
| Job scheduling | Quartz JDBC store | `@Scheduled` cron | Survives restarts; cluster-safe; UI-manageable |
| Frontend offline | IndexedDB (Dexie.js) | Service Worker cache-only | Fine-grained record-level sync control with idempotency |
