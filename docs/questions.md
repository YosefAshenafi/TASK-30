# Clarification Questions — Meridian Training Analytics Management System

---

## Question 1

### Question
What token-based authentication mechanism should be used for the REST API — short-lived JWTs with a refresh-token rotation flow, or opaque session tokens managed server-side — and what should the access-token TTL and refresh-token TTL be?

### Assumption
JWTs are used with a 15-minute access token and a 7-day sliding refresh token stored in an HttpOnly cookie, with server-side refresh-token rotation on each use to prevent replay.

### Suggested Solution
Implement stateless JWT authentication (access token: 15 min, refresh token: 7 days, HttpOnly + Secure cookie). Add a server-side refresh-token allow-list per user so tokens can be revoked immediately on logout or anomaly detection. This fits the offline-capable architecture because the Angular client can re-issue the access token from the refresh cookie without requiring a full login.

---

## Question 2

### Question
When a student completes or partially completes a training session offline and the same course or activity has been structurally changed (e.g., activities reordered, an item removed, or the student unenrolled) on the server before sync occurs, what is the correct reconciliation behavior — apply the offline session as-is, reject it with a user-visible error, or merge it under a conflict-review queue?

### Assumption
Last-write-wins is applied at the session-log level (not at the course-structure level): the offline session record is persisted as submitted, flagged with a `sync_warning` status if the course version has changed, and surfaced to the instructor for review rather than silently discarded or silently merged.

### Suggested Solution
Store the `course_version_id` captured at session start inside the IndexedDB payload. During sync, if the server's current version differs, persist the session with status `SYNC_VERSION_MISMATCH`, emit an in-app notification to the owning instructor, and display a non-blocking banner to the student. This avoids silent data loss while keeping the sync path non-interactive.

---

## Question 3

### Question
How should multi-tenant data isolation be enforced for corporate mentors — row-level security in PostgreSQL (RLS policies keyed to `organization_id`), application-layer `WHERE organization_id = :orgId` guards, or a separate schema/database per organization?

### Assumption
Row-level security is implemented as PostgreSQL RLS policies on all organization-scoped tables (enrollments, purchases, learner profiles, session logs), complemented by Spring Security method-level checks that prevent cross-organization API calls at the service layer.

### Suggested Solution
Use PostgreSQL RLS as the enforcement backstop (so a misconfigured service layer cannot leak data) combined with Spring Security `@PreAuthorize` expressions that assert `principal.organizationId == resource.organizationId`. Single-schema multi-tenant is sufficient for the on-premise scale described; separate schemas add operational complexity without benefit at this scale.

---

## Question 4

### Question
What constitutes a "new device fingerprint" for anomaly detection — a hash of User-Agent + browser language + timezone + screen resolution, a more formal fingerprint library (e.g., FingerprintJS), or simply a first-seen (IP, User-Agent) pair per user?

### Assumption
A device fingerprint is a server-side SHA-256 hash of `(User-Agent, Accept-Language, timezone-offset)` stored in the `audit_device_fingerprints` table per user. A mismatch against the user's known fingerprints triggers an in-app anomaly notification to the Administrator; no external fingerprinting library is required.

### Suggested Solution
Use the lightweight hash approach (no external dependency) with a per-user allow-list of up to 5 known fingerprints. Fingerprints are added to the allow-list after the user acknowledges the new-device notification. This avoids shipping a third-party fingerprinting SDK to an air-gapped on-premise environment.

---

## Question 5

### Question
How should allowed IP ranges for anomaly detection be configured — a global system-wide allowlist editable only by Administrators, a per-user allowlist set by the user or an Admin, or a per-role policy (e.g., all Faculty Mentors must connect from the campus subnet)?

### Assumption
IP ranges are configured globally by Administrators in a `security_policy` settings table, with optional per-user overrides. Access from outside the configured ranges generates an in-app anomaly notification but does not automatically block the request (warn-only mode by default, blockable via a separate admin toggle).

### Suggested Solution
Model IP policy as a `security_policy` record with `allowed_cidr_ranges[]`, `enforcement_mode` (WARN | BLOCK), and an optional `user_id` for overrides. Default to WARN mode so administrators are not locked out during initial configuration. Expose the policy editor in the Admin security settings panel.

---

## Question 6

### Question
What is the delivery mechanism for in-app notifications (anomaly alerts, export completions, approval requests, account pending/approved status) — server-sent events (SSE) for a lightweight unidirectional push, WebSocket for bidirectional real-time messaging, or HTTP polling as a fallback-only path?

### Assumption
SSE is used as the primary delivery channel from the Spring Boot backend (`/api/notifications/stream`), with a client-side polling fallback (every 30 seconds) when SSE is unavailable. Notifications are persisted to a `notifications` table regardless of delivery success so they survive page refreshes.

### Suggested Solution
SSE fits the on-premise, single-server topology better than WebSocket because it requires no additional broker and the Angular `EventSource` API handles reconnection automatically. Persist all notifications server-side with `read_at` and `acknowledged_at` timestamps; the SSE stream pushes only new events, and the client fetches the full unread list on login.

---

## Question 7

### Question
Should backup files written to the administrator-defined local path be encrypted at rest using AES-256 (matching the application-data encryption policy), and if so, should the encryption key be stored in the application keystore, derived from an administrator-supplied passphrase, or managed by the OS keychain?

### Assumption
Backup files are AES-256-GCM encrypted before writing to disk. The encryption key is derived from an administrator-supplied passphrase using PBKDF2 (100,000 iterations, SHA-256) and stored in a `backup_keys` configuration record encrypted with the application master key. The plaintext passphrase is never persisted.

### Suggested Solution
Use a two-key model: a per-backup data encryption key (DEK) encrypted by a key encryption key (KEK) derived from the admin passphrase. Store the encrypted DEK alongside each backup file. This allows passphrase rotation without re-encrypting all existing backups and aligns with standard at-rest encryption practices for on-premise systems.

---

## Question 8

### Question
What scheduling engine should drive the timed CSV/PDF export jobs — the embedded Quartz Scheduler (persisted job store in PostgreSQL), a simple `@Scheduled` Spring annotation with cron expressions managed in the database, or an external OS-level cron job that triggers an API endpoint?

### Assumption
Quartz Scheduler with a JDBC job store (backed by the existing PostgreSQL instance) is used so that export schedules survive application restarts, support cluster-safe locking if a standby server is promoted, and are manageable through the Admin UI without requiring OS-level access.

### Suggested Solution
Use Quartz with the `org.quartz.impl.jdbcjobstore.JobStoreTX` store. Expose a `POST /api/reports/schedules` endpoint for creating/updating jobs via the Admin UI, which translates cron-expression inputs into Quartz `CronTrigger` records. This keeps schedule management entirely within the application and avoids SSH access to the server for schedule changes.

---

## Question 9

### Question
What is the expected concurrent-user scale and data volume for the on-premise deployment — specifically, the maximum simultaneous sessions, total learner records, and assessment-attempt rows — so that indexing strategy, connection-pool sizing, and IndexedDB sync-batch limits can be set appropriately?

### Assumption
The system is sized for up to 500 concurrent users, 50,000 total learner records, and 5 million assessment-attempt rows. PostgreSQL connection pool is set to 20 active + 10 idle (HikariCP defaults). IndexedDB sync batches are capped at 500 records per request to prevent large payload timeouts on slow LAN links.

### Suggested Solution
Document the sizing assumptions in `docs/design.md` and expose them as configurable Spring Boot properties (`app.sync.batch-size`, `spring.datasource.hikari.maximum-pool-size`) so operators can tune for their hardware without code changes. Add a `/api/health/db` endpoint that reports pool utilization for capacity monitoring.

---

## Question 10

### Question
For the 14-day recycle bin (soft-delete for accidental deletions), which entity types are covered — all top-level records (users, courses, enrollments, sessions), only administrator-initiated hard deletes, or a configurable per-entity-type policy? And who can restore items from the recycle bin — only Administrators, or also the Faculty/Corporate Mentor who owns the record?

### Assumption
The recycle bin covers administrator-initiated deletes on users, courses, and enrollment records. Soft-deleted rows are retained in the same table with a `deleted_at` timestamp and a `deleted_by` foreign key. Only Administrators can browse and restore items from the recycle bin UI. After 14 days, a scheduled Quartz job permanently purges soft-deleted rows and writes a purge audit event.

### Suggested Solution
Add `deleted_at TIMESTAMPTZ` and `deleted_by UUID` columns to the covered tables, and update all standard queries with `WHERE deleted_at IS NULL`. Expose a `GET /api/admin/recycle-bin` endpoint with entity-type filtering and a `POST /api/admin/recycle-bin/{id}/restore` endpoint. Gate both endpoints with `ROLE_ADMINISTRATOR` so no lower role can access or restore deleted records.
