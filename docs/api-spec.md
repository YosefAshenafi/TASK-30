# API Specifications — Meridian Training Analytics Management System

These are **real contracts** served by the Spring Boot backend and consumed by the Angular client. When the client is offline, mutating calls are queued in IndexedDB and replayed against these contracts on LAN reconnect — the server honors `Idempotency-Key` so the contract is stable across online/offline paths.

## 0. Conventions

| Item | Value |
|---|---|
| Base URL | `https://{on-prem-host}/api/v1` |
| Transport | HTTPS only (locally managed cert); HTTP rejected |
| Auth | `Authorization: Bearer <accessToken>` (JWT, 15 min TTL) |
| Content type | `application/json; charset=utf-8` |
| IDs | UUID v7 (lexicographically sortable by time) |
| Timestamps | RFC 3339 UTC, millisecond precision (`2026-04-20T17:03:21.492Z`) |
| Idempotency | `Idempotency-Key: <uuid>` header on every mutating call; cached 72h |
| Rate limits | Default 120 req/min per user; export endpoints 30/min; login 10/min per IP |
| Pagination | `?page=0&size=50` (max 200); response includes `{ items, page, size, total }` |
| Errors | `{ "error": { "code": "STRING", "message": "...", "details": {...} } }` |
| Security headers | `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy`, `Referrer-Policy: strict-origin-when-cross-origin` |

### Shared DTOs

```ts
type UUID = string;                 // UUID v7
type ISODateTime = string;          // RFC 3339 UTC
type Role = 'STUDENT' | 'CORPORATE_MENTOR' | 'FACULTY_MENTOR' | 'ADMIN';
type Classification = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';
type Status = 'PENDING' | 'ACTIVE' | 'LOCKED' | 'SUSPENDED' | 'DELETED';

interface ErrorEnvelope {
  error: { code: string; message: string; details?: Record<string, unknown> };
}

interface Page<T> { items: T[]; page: number; size: number; total: number; }
```

---

## 1. Authentication & Accounts

### 1.1 Register

`POST /auth/register`

```ts
interface RegisterRequest {
  username: string;            // 3-64 chars
  password: string;            // ≥12 chars, ≥1 number, ≥1 symbol
  displayName: string;
  requestedRole: Role;         // approval still required
  organizationCode?: string;   // required if role = CORPORATE_MENTOR
}

interface RegisterResponse {
  userId: UUID;
  status: 'PENDING';
  approvalSlaBusinessDays: 2;
}
```

Status codes: `201 Created`, `400 VALIDATION_FAILED`, `409 USERNAME_TAKEN`.

### 1.2 Login

`POST /auth/login`

```ts
interface LoginRequest {
  username: string;
  password: string;
  deviceFingerprint: string;   // client-computed SHA-256
}

interface LoginResponse {
  accessToken: string;         // JWT, 15-minute TTL
  refreshToken: string;        // rotating, 12h absolute
  expiresIn: 900;
  user: UserProfile;
  newDeviceAlertRaised: boolean;
}
```

Status codes: `200`, `401 INVALID_CREDENTIALS`, `403 ACCOUNT_PENDING | ACCOUNT_LOCKED | IP_NOT_ALLOWED`, `429 TOO_MANY_ATTEMPTS`.

**Lockout rule**: 5 failed attempts within 15 minutes → account `LOCKED` for 15 minutes; counter resets on successful login or elapsed window.

### 1.3 Refresh / Logout

```
POST /auth/refresh    { refreshToken } -> LoginResponse
POST /auth/logout     { refreshToken } -> 204
```

### 1.4 Admin approvals

```
GET  /admin/users?status=PENDING                 -> Page<UserSummary>
POST /admin/users/{id}/approve                   -> 204
POST /admin/users/{id}/reject  { reason }        -> 204
POST /admin/users/{id}/unlock                    -> 204
```

### 1.5 User profile

```ts
interface UserProfile {
  id: UUID;
  username: string;
  displayName: string;
  role: Role;
  status: Status;
  organizationId: UUID | null;
  allowedIpRanges: string[];   // CIDRs evaluated at login
  lastLoginAt: ISODateTime | null;
  createdAt: ISODateTime;
}
```

---

## 2. Courses, Cohorts & Assessment Items

```ts
interface Course {
  id: UUID;
  code: string;
  title: string;
  version: string;             // used by analytics filters
  locationId: UUID | null;
  instructorId: UUID | null;
  classification: Classification;
  createdAt: ISODateTime;
}

interface Cohort {
  id: UUID;
  courseId: UUID;
  name: string;
  totalSeats: number;
  startsAt: ISODateTime;
  endsAt: ISODateTime;
}

interface AssessmentItem {
  id: UUID;
  courseId: UUID;
  knowledgePointId: UUID;
  type: 'SINGLE' | 'MULTI' | 'SHORT' | 'CODE';
  difficulty: number;          // 0.0 – 1.0, computed from attempts
  discrimination: number;      // -1.0 – 1.0
  stem: string;
  choices?: { id: string; label: string; correct: boolean }[];
}
```

Endpoints:

```
GET    /courses?version=&location=&instructor=&q=       -> Page<Course>
POST   /courses                                         -> Course
PUT    /courses/{id}                                    -> Course
GET    /courses/{id}/cohorts                            -> Cohort[]
GET    /courses/{id}/assessment-items                   -> Page<AssessmentItem>
POST   /assessment-items                                -> AssessmentItem
```

---

## 3. Training Sessions (offline-first)

The canonical offline write path. Client queues these; server accepts them in any order thanks to `Idempotency-Key` + `clientUpdatedAt`.

```ts
interface TrainingSession {
  id: UUID;                    // client-issued UUID v7
  studentId: UUID;
  courseId: UUID;
  cohortId: UUID | null;
  startedAt: ISODateTime;
  endedAt: ISODateTime | null;
  restSecondsDefault: number;  // 15-300, default 60
  status: 'IN_PROGRESS' | 'PAUSED' | 'COMPLETED' | 'ABANDONED';
  clientUpdatedAt: ISODateTime;
}

interface SessionActivitySet {
  id: UUID;
  sessionId: UUID;
  activityId: UUID;
  setIndex: number;            // 1-based, monotonic per activity
  restSeconds: number;         // 15-300
  completedAt: ISODateTime | null;
  notes: string | null;
  clientUpdatedAt: ISODateTime;
}
```

Endpoints:

```
POST /sessions                                    -> TrainingSession          (201)
PATCH /sessions/{id}                              -> TrainingSession          (200)
POST /sessions/{id}/continue                      -> TrainingSession          (200)   // one-tap resume
POST /sessions/{id}/complete                      -> TrainingSession          (200)
POST /sessions/{id}/sets                          -> SessionActivitySet       (201)
PATCH /sessions/{id}/sets/{setId}                 -> SessionActivitySet       (200)
POST /sessions/sync                               -> SyncResult               (200)   // bulk replay
GET  /sessions?studentId=&from=&to=               -> Page<TrainingSession>
```

```ts
interface SyncResult {
  applied: { id: UUID; kind: 'session' | 'set'; status: 'CREATED' | 'UPDATED' | 'NOOP' }[];
  conflicts: { id: UUID; reason: 'OLDER_CLIENT_TIMESTAMP' | 'IDEMPOTENCY_MISMATCH'; serverVersion: unknown }[];
}
```

**Conflict rules**:

1. Same `Idempotency-Key` + same body → cached response returned (`NOOP`).
2. Same `Idempotency-Key` + different body → `409 IDEMPOTENCY_MISMATCH`.
3. Different key but same entity id → last-write-wins by `clientUpdatedAt`; ties broken lexicographically by `Idempotency-Key`.

---

## 4. Analytics

All endpoints accept the same filter set:

```ts
interface AnalyticsFilter {
  from?: ISODateTime;
  to?: ISODateTime;
  locationId?: UUID;
  instructorId?: UUID;
  courseId?: UUID;
  courseVersion?: string;
  cohortId?: UUID;
  learnerId?: UUID;            // only for Student self-view or ADMIN
}
```

```
GET /analytics/mastery-trends              -> MasteryTrendSeries
GET /analytics/wrong-answers               -> WrongAnswerDistribution
GET /analytics/weak-knowledge-points       -> WeakKnowledgePointList
GET /analytics/item-stats                  -> ItemStatsList
```

```ts
interface MasteryTrendSeries {
  scope: 'LEARNER' | 'COHORT' | 'COURSE';
  points: { at: ISODateTime; masteryPct: number; attempts: number }[];
}

interface WrongAnswerDistribution {
  items: { itemId: UUID; stemPreview: string; wrongChoiceId: string; count: number; pct: number }[];
}

interface WeakKnowledgePointList {
  items: { knowledgePointId: UUID; name: string; masteryPct: number; attemptVolume: number }[];
}

interface ItemStatsList {
  items: { itemId: UUID; difficulty: number; discrimination: number; attempts: number }[];
}
```

Scope enforcement: Corporate mentors are server-side filtered to their `organizationId`; Students to their own `learnerId`.

---

## 5. Operations & Reporting Center

```ts
type ReportKind =
  | 'ENROLLMENTS'
  | 'SEAT_UTILIZATION'
  | 'REFUND_RETURN_RATE'
  | 'INVENTORY_LEVELS'
  | 'CERT_EXPIRING';            // supports 30/60/90 windows

interface ReportRequest {
  kind: ReportKind;
  window?: 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'CUSTOM';
  from?: ISODateTime;
  to?: ISODateTime;
  certExpiringDays?: 30 | 60 | 90;
  format: 'CSV' | 'PDF' | 'JSON';
  schedule?: { cron: string; localPath: string };   // local CSV/PDF exports
  organizationId?: UUID;                            // admin override
}

interface ReportRun {
  id: UUID;
  kind: ReportKind;
  status: 'QUEUED' | 'RUNNING' | 'NEEDS_APPROVAL' | 'SUCCEEDED' | 'FAILED';
  rowCount: number | null;
  outputPath: string | null;    // local filesystem path
  requestedBy: UUID;
  approvalRequestId: UUID | null;
  classification: Classification;
  createdAt: ISODateTime;
  completedAt: ISODateTime | null;
}
```

Endpoints:

```
POST /reports                 -> ReportRun     (202 accepted, may be NEEDS_APPROVAL)
GET  /reports/{id}            -> ReportRun
GET  /reports                 -> Page<ReportRun>
POST /reports/{id}/cancel     -> 204
GET  /reports/schedules       -> Page<ReportSchedule>
POST /reports/schedules       -> ReportSchedule
DELETE /reports/schedules/{id}-> 204
```

**Approval threshold**: `rowCount > 10000` OR `classification = 'RESTRICTED'` ⇒ auto-enters `NEEDS_APPROVAL`; the run does not execute until an Administrator approves.

---

## 6. Approval Workflow

```ts
type ApprovalType = 'PERMISSION_CHANGE' | 'EXPORT';
type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED';

interface ApprovalRequest {
  id: UUID;
  type: ApprovalType;
  payload: Record<string, unknown>;
  status: ApprovalStatus;
  requestedBy: UUID;
  reviewedBy: UUID | null;
  reason: string | null;
  createdAt: ISODateTime;
  decidedAt: ISODateTime | null;
  expiresAt: ISODateTime;      // createdAt + 7 days
}
```

```
GET  /approvals?status=PENDING              -> Page<ApprovalRequest>
POST /approvals/{id}/approve  { reason? }   -> ApprovalRequest
POST /approvals/{id}/reject   { reason }    -> ApprovalRequest
```

---

## 7. Notifications

```ts
interface NotificationTemplate {
  id: UUID;
  key: string;                 // e.g. export.ready, anomaly.newDevice
  subject: string;
  bodyMarkdown: string;
  variables: string[];
  updatedBy: UUID;
  updatedAt: ISODateTime;
}

interface InAppNotification {
  id: UUID;
  recipientId: UUID;
  templateKey: string;
  rendered: { subject: string; bodyHtml: string };
  severity: 'INFO' | 'WARN' | 'CRITICAL';
  readAt: ISODateTime | null;
  createdAt: ISODateTime;
}
```

```
GET    /notifications?unread=true           -> Page<InAppNotification>
POST   /notifications/{id}/read             -> 204
GET    /admin/notification-templates        -> Page<NotificationTemplate>
PUT    /admin/notification-templates/{key}  -> NotificationTemplate
```

---

## 8. Audit, Anomalies, Governance

```ts
interface AuditEvent {
  id: UUID;
  actorId: UUID | null;
  action: 'LOGIN' | 'LOGOUT' | 'EXPORT_ATTEMPT' | 'EXPORT_SUCCESS'
        | 'PERMISSION_CHANGE' | 'DATA_DELETE' | 'DATA_RESTORE';
  targetType: string;
  targetId: UUID | null;
  sourceIp: string;
  deviceFingerprint: string | null;
  classification: Classification;
  createdAt: ISODateTime;
  metadata: Record<string, unknown>;
}
```

```
GET /admin/audit?action=&from=&to=&actorId=   -> Page<AuditEvent>
GET /admin/anomalies?resolved=false           -> Page<AnomalyEvent>
POST /admin/anomalies/{id}/resolve            -> 204
GET /admin/allowed-ip-ranges                  -> AllowedIpRange[]
POST /admin/allowed-ip-ranges                 -> AllowedIpRange
DELETE /admin/allowed-ip-ranges/{id}          -> 204
```

Anomaly triggers (server-side, evaluated each minute):

| Trigger | Condition |
|---|---|
| `anomaly.newDevice` | Login fingerprint not in `user_device_fingerprints` |
| `anomaly.ipOutOfRange` | `sourceIp` not in any applicable `allowed_ip_ranges` CIDR |
| `anomaly.exportBurst` | `>20` `EXPORT_ATTEMPT` events in last 10 minutes per user OR per source IP |

---

## 9. Recycle Bin & Backups

```
GET    /admin/recycle-bin?type=users|enrollments|...    -> Page<RecycleBinEntry>
POST   /admin/recycle-bin/{type}/{id}/restore           -> 204
DELETE /admin/recycle-bin/{type}/{id}                   -> 204   // hard-delete, audited

GET    /admin/backups                                   -> BackupRun[]
POST   /admin/backups/run?mode=FULL|INCREMENTAL         -> BackupRun   (202)
GET    /admin/backups/policy                            -> BackupPolicy
PUT    /admin/backups/policy                            -> BackupPolicy
POST   /admin/backups/recovery-drill                    -> RecoveryDrill (202)
GET    /admin/backups/recovery-drills                   -> Page<RecoveryDrill>
```

```ts
interface BackupPolicy {
  localPath: string;
  retentionDays: number;       // default 30
  incrementalCron: string;     // default '0 2 * * *'
  fullCron: string;            // default '0 3 * * 0'
  recycleBinEnabled: boolean;
  recycleBinRetentionDays: 14;
}
```

Retention: automated entries older than 30 days are purged by a nightly job; recycle bin entries older than 14 days are hard-deleted.

---

## 10. Rate Limits (summary)

| Endpoint group | Limit |
|---|---|
| `POST /auth/login` | 10/min per IP |
| `POST /auth/register` | 5/min per IP |
| `POST /reports`, `POST /reports/schedules` | 30/min per user |
| `POST /sessions/sync` | 60/min per user |
| Default | 120/min per user |

Exceeding a limit returns `429 RATE_LIMITED` with `Retry-After` in seconds.
