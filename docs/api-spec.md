# Meridian REST API Specification

Base URL: `http://localhost:8080`  
All endpoints are prefixed with `/api`.  
All request and response bodies are `application/json` unless noted.

## Authentication

All protected endpoints require:
```
Authorization: Bearer <accessToken>
```
The access token is a 15-minute JWT issued by `POST /api/auth/login`. A 7-day refresh token is delivered as an `HttpOnly; Secure` cookie and rotated by `POST /api/auth/refresh`.

---

## Error Response Shape

All error responses use this shape (REQ-067):
```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "timestamp": "2024-01-15T10:30:00Z",
  "details": ["field: message"]   // present on validation errors only
}
```

Standard status codes (REQ-068):

| Code | Meaning |
|---|---|
| 200 | OK |
| 201 | Created |
| 400 | Validation / bad request |
| 401 | Unauthenticated |
| 403 | Forbidden (role or object-level authz) |
| 404 | Not found |
| 409 | Conflict (duplicate) |
| 422 | Unprocessable entity |
| 500 | Internal server error |

---

## Phase 2 — Authentication & User Management

### POST /api/auth/register
Register a new user account. Account is created with `status=PENDING` pending administrator approval.

**Auth required:** No  
**REQ:** REQ-003, REQ-006, REQ-065

**Request:**
```json
{
  "username": "jsmith",
  "password": "MyPass@12345",
  "organizationId": "uuid-optional"
}
```

**Constraints:**
- `username`: 3–100 characters, unique
- `password`: ≥12 characters, ≥1 digit, ≥1 symbol

**Response 201:**
```json
{
  "id": "uuid",
  "username": "jsmith",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

**Errors:** `400` validation failure · `409` username already taken

---

### POST /api/auth/login
Authenticate and receive tokens.

**Auth required:** No  
**REQ:** REQ-007, REQ-051, REQ-065

**Request:**
```json
{
  "username": "jsmith",
  "password": "MyPass@12345"
}
```

**Response 200:**
```json
{
  "accessToken": "<jwt>",
  "expiresIn": 900
}
```
Sets `Set-Cookie: refresh=<token>; HttpOnly; Secure; Path=/api/auth/refresh`

**Errors:** `401` invalid credentials · `401` account not yet approved (status=PENDING) · `401` account locked (includes `lockedUntil` in message) · `403` account rejected

---

### POST /api/auth/logout
Revoke the refresh token and clear the cookie.

**Auth required:** Yes  
**REQ:** REQ-051, REQ-061

**Request:** (no body)

**Response 204:** (no body)

---

### POST /api/auth/refresh
Exchange the refresh cookie for a new access token. Rotates the refresh token.

**Auth required:** No (uses `refresh` cookie)  
**REQ:** REQ-051

**Request:** (no body — refresh token read from cookie)

**Response 200:**
```json
{
  "accessToken": "<new-jwt>",
  "expiresIn": 900
}
```
Sets new `refresh` cookie; old token is revoked.

**Errors:** `401` missing or invalid refresh token · `401` refresh token revoked

---

### GET /api/auth/me
Return the authenticated user's profile (masked sensitive fields unless permitted).

**Auth required:** Yes  
**Object-level authz:** Own profile only  
**REQ:** REQ-061, REQ-062, REQ-065

**Response 200:**
```json
{
  "id": "uuid",
  "username": "jsmith",
  "roles": ["ROLE_STUDENT"],
  "status": "ACTIVE",
  "organizationId": "uuid-or-null",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

**Errors:** `401` unauthenticated

---

### GET /api/admin/users/pending
List all accounts awaiting approval.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-004, REQ-064

**Query params:** `page` (0-based), `size` (default 20)

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "username": "newuser",
      "status": "PENDING",
      "createdAt": "2024-01-15T10:00:00Z"
    }
  ],
  "totalElements": 5,
  "page": 0,
  "size": 20
}
```

**Errors:** `401` · `403` non-admin

---

### PUT /api/admin/users/{id}/approve
Approve a pending account.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-004, REQ-005, REQ-064

**Path:** `id` — user UUID

**Response 200:**
```json
{
  "id": "uuid",
  "username": "newuser",
  "status": "ACTIVE"
}
```

**Errors:** `401` · `403` · `404` user not found · `409` user not in PENDING state

---

### PUT /api/admin/users/{id}/reject
Reject a pending account.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-004, REQ-064

**Request:**
```json
{
  "reason": "Optional rejection note"
}
```

**Response 200:**
```json
{
  "id": "uuid",
  "status": "REJECTED"
}
```

**Errors:** `401` · `403` · `404` · `409` user not in PENDING state

---

### GET /api/admin/users
List all users (all statuses).

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-001, REQ-064

**Query params:** `status`, `role`, `page`, `size`

**Response 200:** (same paginated shape as `/pending`)

**Errors:** `401` · `403`

---

### PATCH /api/admin/users/{id}/role
Assign a role to a user.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-001, REQ-064

**Request:**
```json
{
  "role": "ROLE_FACULTY_MENTOR"
}
```

**Response 200:**
```json
{
  "id": "uuid",
  "username": "jsmith",
  "roles": ["ROLE_FACULTY_MENTOR"]
}
```

**Errors:** `401` · `403` · `404` · `400` invalid role name

---

### GET /api/courses
List all courses (paginated).

**Auth required:** Yes  
**REQ:** REQ-002, REQ-061

**Query params:** `page`, `size`, `version`, `location`, `instructor`

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "title": "Safety Fundamentals",
      "version": "2.1",
      "location": "Building A",
      "instructor": "Dr. Kim",
      "createdAt": "2024-01-01T00:00:00Z"
    }
  ],
  "totalElements": 12,
  "page": 0,
  "size": 20
}
```

**Errors:** `401`

---

### POST /api/courses
Create a new course.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR or ROLE_FACULTY_MENTOR  
**REQ:** REQ-002, REQ-064

**Request:**
```json
{
  "title": "Safety Fundamentals",
  "version": "1.0",
  "location": "Building A",
  "instructor": "Dr. Kim"
}
```

**Response 201:**
```json
{
  "id": "uuid",
  "title": "Safety Fundamentals",
  "version": "1.0",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

**Errors:** `400` · `401` · `403`

---

### GET /api/courses/{id}
Retrieve a single course.

**Auth required:** Yes  
**REQ:** REQ-061

**Response 200:** Single course object (same shape as list item).

**Errors:** `401` · `404`

---

### PUT /api/courses/{id}
Update course metadata.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR or ROLE_FACULTY_MENTOR  
**REQ:** REQ-002, REQ-064

**Request:** Same shape as POST (partial updates accepted; unset fields unchanged).

**Response 200:** Updated course object.

**Errors:** `400` · `401` · `403` · `404`

---

## Phase 3 — Student Training Session Module

### POST /api/sessions
Start a new training session.

**Auth required:** Yes  
**REQ:** REQ-008, REQ-061

**Request:**
```json
{
  "courseId": "uuid",
  "restTimerSecs": 60,
  "idempotencyKey": "uuid-v4"
}
```

**Constraints:** `restTimerSecs` ∈ [15, 300]; default 60.

**Response 201:**
```json
{
  "id": "uuid",
  "courseId": "uuid",
  "status": "IN_PROGRESS",
  "restTimerSecs": 60,
  "startedAt": "2024-01-15T10:00:00Z",
  "courseVersion": "2.1"
}
```

**Errors:** `400` · `401` · `404` course not found · `409` idempotency key already used

---

### GET /api/sessions
List the authenticated student's sessions.

**Auth required:** Yes · **Scope:** Student sees only own sessions; Mentors/Admins see org-scoped  
**REQ:** REQ-008, REQ-062, REQ-063

**Query params:** `status`, `courseId`, `page`, `size`

**Response 200:** Paginated list of session objects.

**Errors:** `401`

---

### GET /api/sessions/{id}
Retrieve a session and its activities.

**Auth required:** Yes · **Object-level authz:** Owner only (or Mentor/Admin in same org)  
**REQ:** REQ-008, REQ-062

**Response 200:**
```json
{
  "id": "uuid",
  "courseId": "uuid",
  "status": "IN_PROGRESS",
  "restTimerSecs": 90,
  "startedAt": "2024-01-15T10:00:00Z",
  "completedAt": null,
  "syncStatus": "SYNCED",
  "activities": [
    { "id": "uuid", "activityRef": "module-1-warmup", "completed": false }
  ]
}
```

**Errors:** `401` · `403` · `404`

---

### PUT /api/sessions/{id}
Update session state (rest timer, resume after interruption).

**Auth required:** Yes · **Object-level authz:** Owner only  
**REQ:** REQ-011, REQ-062

**Request:**
```json
{
  "restTimerSecs": 120,
  "status": "IN_PROGRESS"
}
```

**Response 200:** Updated session object.

**Errors:** `400` · `401` · `403` · `404`

---

### POST /api/sessions/{id}/complete
Mark a session complete.

**Auth required:** Yes · **Object-level authz:** Owner only  
**REQ:** REQ-008, REQ-062

**Request:** (no body)

**Response 200:**
```json
{
  "id": "uuid",
  "status": "COMPLETED",
  "completedAt": "2024-01-15T11:30:00Z"
}
```

**Errors:** `401` · `403` · `404` · `409` session already completed

---

### GET /api/sessions/{id}/activities
List all activities for a session.

**Auth required:** Yes · **Object-level authz:** Owner only  
**REQ:** REQ-011, REQ-062

**Response 200:**
```json
[
  {
    "id": "uuid",
    "activityRef": "module-1-warmup",
    "completed": true,
    "completedAt": "2024-01-15T10:15:00Z"
  }
]
```

**Errors:** `401` · `403` · `404`

---

### POST /api/sessions/sync
Bulk-sync offline session records (from IndexedDB).

**Auth required:** Yes · **Object-level authz:** Owner of each record  
**REQ:** REQ-034, REQ-035, REQ-036, REQ-062

**Request:**
```json
{
  "records": [
    {
      "idempotencyKey": "uuid-v4",
      "courseId": "uuid",
      "courseVersion": "2.1",
      "restTimerSecs": 60,
      "status": "COMPLETED",
      "startedAt": "2024-01-15T10:00:00Z",
      "completedAt": "2024-01-15T11:00:00Z",
      "activities": [
        { "activityRef": "module-1", "completed": true, "completedAt": "2024-01-15T10:15:00Z" }
      ]
    }
  ]
}
```

**Constraints:** ≤500 records per batch.

**Response 200:**
```json
{
  "results": [
    {
      "idempotencyKey": "uuid-v4",
      "status": "SYNCED",
      "sessionId": "uuid"
    },
    {
      "idempotencyKey": "uuid-v4-2",
      "status": "SYNC_VERSION_MISMATCH",
      "sessionId": "uuid",
      "warning": "Course version changed since session start"
    },
    {
      "idempotencyKey": "uuid-v4-3",
      "status": "DUPLICATE",
      "sessionId": "uuid"
    }
  ]
}
```

**Errors:** `400` batch > 500 · `401`

---

## Phase 4 — Assessment & Analytics Engine

All analytics endpoints require **ROLE_MENTOR (CORPORATE or FACULTY) or ROLE_ADMINISTRATOR** (REQ-020).  
All support filter params: `startDate`, `endDate`, `location`, `instructor`, `courseVersion` (REQ-019).

### GET /api/analytics/mastery
Mastery trend data at a specified granularity.

**Auth required:** Yes · **Role:** MENTOR or ADMIN  
**REQ:** REQ-014, REQ-018, REQ-020

**Query params:** `granularity` (LEARNER | COHORT | COURSE), `courseId`, `startDate`, `endDate`, `location`, `instructor`, `courseVersion`

**Response 200:**
```json
{
  "granularity": "COHORT",
  "dataPoints": [
    {
      "label": "Cohort A",
      "masteryScore": 0.78,
      "trend": [
        { "date": "2024-01-01", "score": 0.71 },
        { "date": "2024-01-08", "score": 0.78 }
      ]
    }
  ]
}
```

**Errors:** `400` invalid params · `401` · `403`

---

### GET /api/analytics/wrong-answers
Wrong-answer distribution analytics.

**Auth required:** Yes · **Role:** MENTOR or ADMIN  
**REQ:** REQ-015, REQ-020

**Query params:** same filter set + `granularity`

**Response 200:**
```json
{
  "items": [
    {
      "assessmentItemId": "uuid",
      "question": "What is the correct PPE for...",
      "wrongAnswerCount": 43,
      "topWrongAnswers": ["Option B", "Option D"]
    }
  ]
}
```

**Errors:** `401` · `403`

---

### GET /api/analytics/knowledge-gaps
Weak knowledge-point analysis.

**Auth required:** Yes · **Role:** MENTOR or ADMIN  
**REQ:** REQ-016, REQ-020

**Response 200:**
```json
{
  "knowledgePoints": [
    {
      "name": "Chemical Handling",
      "averageScore": 0.52,
      "learnerCount": 34
    }
  ]
}
```

**Errors:** `401` · `403`

---

### GET /api/analytics/item-difficulty
Item difficulty and discrimination metrics.

**Auth required:** Yes · **Role:** MENTOR or ADMIN  
**REQ:** REQ-017, REQ-020

**Response 200:**
```json
{
  "items": [
    {
      "assessmentItemId": "uuid",
      "difficulty": 0.62,
      "discrimination": 0.41
    }
  ]
}
```

**Errors:** `401` · `403`

---

### GET /api/analytics/learner/{userId}
Analytics for a specific learner (org-scoped).

**Auth required:** Yes · **Role:** MENTOR or ADMIN · **Object-level authz:** Same org  
**REQ:** REQ-018, REQ-020, REQ-062

**Response 200:**
```json
{
  "userId": "uuid",
  "overallMastery": 0.81,
  "completedSessions": 12,
  "knowledgeGaps": ["Chemical Handling", "Emergency Protocols"]
}
```

**Errors:** `401` · `403` cross-org · `404`

---

### GET /api/analytics/cohort/{cohortId}
Analytics for a cohort (org-scoped).

**Auth required:** Yes · **Role:** MENTOR or ADMIN · **Object-level authz:** Same org  
**REQ:** REQ-018, REQ-020, REQ-062

**Response 200:**
```json
{
  "cohortId": "uuid",
  "learnerCount": 24,
  "averageMastery": 0.74,
  "masteryTrend": []
}
```

**Errors:** `401` · `403` · `404`

---

### GET /api/analytics/course/{courseId}
Analytics for a course across all learners (org-scoped).

**Auth required:** Yes · **Role:** MENTOR or ADMIN · **Object-level authz:** Same org  
**REQ:** REQ-018, REQ-020, REQ-062

**Response 200:**
```json
{
  "courseId": "uuid",
  "title": "Safety Fundamentals",
  "enrollmentCount": 150,
  "averageMastery": 0.76,
  "completionRate": 0.88
}
```

**Errors:** `401` · `403` · `404`

---

## Phase 5 — Operations & Reporting Center

All report endpoints are org-scoped (Corporate Mentors see only their org — REQ-029).

### GET /api/reports/enrollments
Enrollment report.

**Auth required:** Yes · **Scope:** Org-scoped  
**REQ:** REQ-021, REQ-029

**Query params:** `startDate`, `endDate`, `courseId`, `page`, `size`

**Response 200:**
```json
{
  "content": [
    {
      "userId": "uuid",
      "username": "jsmith",
      "courseId": "uuid",
      "courseTitle": "Safety Fundamentals",
      "enrolledAt": "2024-01-10T00:00:00Z",
      "status": "COMPLETED"
    }
  ],
  "totalElements": 87
}
```

**Errors:** `401` · `403`

---

### GET /api/reports/seat-utilization
Seat utilization report.

**Auth required:** Yes · **Scope:** Org-scoped  
**REQ:** REQ-022, REQ-029

**Query params:** `startDate`, `endDate`, `courseId`

**Response 200:**
```json
{
  "courses": [
    {
      "courseId": "uuid",
      "title": "Safety Fundamentals",
      "totalSeats": 30,
      "enrolledSeats": 27,
      "utilizationRate": 0.90
    }
  ]
}
```

**Errors:** `401` · `403`

---

### GET /api/reports/refunds
Refund and return rate report.

**Auth required:** Yes · **Scope:** Org-scoped  
**REQ:** REQ-023, REQ-029

**Response 200:**
```json
{
  "totalTransactions": 200,
  "refunds": 8,
  "returnRate": 0.04
}
```

**Errors:** `401` · `403`

---

### GET /api/reports/inventory
Training-material inventory report.

**Auth required:** Yes · **Scope:** Org-scoped  
**REQ:** REQ-024, REQ-029

**Response 200:**
```json
{
  "materials": [
    {
      "itemId": "uuid",
      "name": "PPE Kit",
      "quantityOnHand": 45,
      "quantityRequired": 60
    }
  ]
}
```

**Errors:** `401` · `403`

---

### GET /api/reports/certifications/expiring
Certifications expiring within 30, 60, or 90 days.

**Auth required:** Yes · **Scope:** Org-scoped  
**REQ:** REQ-025, REQ-029

**Query params:** `window` (30 | 60 | 90, default 30)

**Response 200:**
```json
{
  "window": 30,
  "expirations": [
    {
      "userId": "uuid",
      "username": "jsmith",
      "certificationName": "First Aid Level 2",
      "expiresAt": "2024-02-10T00:00:00Z"
    }
  ]
}
```

**Errors:** `400` invalid window · `401` · `403`

---

### POST /api/reports/schedules
Create a scheduled export job.

**Auth required:** Yes · **Role:** ADMIN or MENTOR  
**REQ:** REQ-026, REQ-027

**Request:**
```json
{
  "reportType": "ENROLLMENTS",
  "cronExpression": "0 6 * * 1",
  "outputFormat": "CSV",
  "outputPath": "/var/meridian/reports"
}
```

**Response 201:**
```json
{
  "id": "uuid",
  "reportType": "ENROLLMENTS",
  "cronExpression": "0 6 * * 1",
  "outputFormat": "CSV",
  "outputPath": "/var/meridian/reports",
  "createdAt": "2024-01-15T10:00:00Z"
}
```

**Errors:** `400` invalid cron · `401` · `403`

---

### GET /api/reports/schedules
List scheduled exports for the requesting user's org.

**Auth required:** Yes · **Scope:** Org-scoped  
**REQ:** REQ-026, REQ-027, REQ-029

**Response 200:** Paginated list of schedule objects.

**Errors:** `401` · `403`

---

### DELETE /api/reports/schedules/{id}
Delete a scheduled export.

**Auth required:** Yes · **Object-level authz:** Owner only  
**REQ:** REQ-026, REQ-027, REQ-062

**Response 204:** (no body)

**Errors:** `401` · `403` · `404`

---

### POST /api/reports/export
Request an immediate report export (triggers approval workflow if above threshold).

**Auth required:** Yes · **Scope:** Org-scoped  
**REQ:** REQ-026, REQ-027, REQ-054

**Request:**
```json
{
  "reportType": "ENROLLMENTS",
  "outputFormat": "PDF",
  "outputPath": "/var/meridian/reports",
  "filters": { "startDate": "2024-01-01", "endDate": "2024-01-31" }
}
```

**Response 202:**
```json
{
  "exportId": "uuid",
  "status": "PENDING_APPROVAL",
  "message": "Export request submitted for approval"
}
```
Or `200` if no approval required.

**Errors:** `400` · `401` · `403`

---

### GET /api/notifications
List unread notifications for the authenticated user.

**Auth required:** Yes · **Object-level authz:** Recipient-scoped  
**REQ:** REQ-028, REQ-062

**Query params:** `read` (true | false), `page`, `size`

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "type": "EXPORT_COMPLETE",
      "subject": "Report Ready",
      "body": "Your enrollment report is available at /var/meridian/reports/...",
      "readAt": null,
      "createdAt": "2024-01-15T10:05:00Z"
    }
  ],
  "totalElements": 3
}
```

**Errors:** `401`

---

### PUT /api/notifications/{id}/read
Mark a notification as read.

**Auth required:** Yes · **Object-level authz:** Recipient only  
**REQ:** REQ-028, REQ-062

**Response 200:**
```json
{
  "id": "uuid",
  "readAt": "2024-01-15T10:10:00Z"
}
```

**Errors:** `401` · `403` · `404`

---

### GET /api/notifications/stream
SSE stream for real-time notifications.

**Auth required:** Yes · **Scope:** Recipient-scoped  
**Content-Type:** `text/event-stream`  
**REQ:** REQ-028, REQ-061

**Event format:**
```
event: notification
data: {"id":"uuid","type":"ANOMALY_ALERT","subject":"New device login detected","createdAt":"..."}
```

**Errors:** `401` (HTTP, before stream opens)

---

## Phase 6 — Data Governance & Security

### GET /api/governance/users/{id}/permissions
View a user's data-access permissions and classification grants.

**Auth required:** Yes · **Object-level authz:** ADMIN or self  
**REQ:** REQ-037, REQ-038, REQ-039

**Response 200:**
```json
{
  "userId": "uuid",
  "grants": [
    {
      "fieldName": "employee_id",
      "classification": "CONFIDENTIAL",
      "grantedBy": "admin-uuid",
      "createdAt": "2024-01-10T00:00:00Z"
    }
  ]
}
```

**Errors:** `401` · `403` · `404`

---

### PUT /api/governance/users/{id}/permissions
Update a user's data permissions (triggers approval workflow).

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-037, REQ-039, REQ-053, REQ-064

**Request:**
```json
{
  "grants": [
    { "fieldName": "employee_id", "classification": "CONFIDENTIAL" }
  ]
}
```

**Response 202:**
```json
{
  "approvalId": "uuid",
  "status": "PENDING",
  "message": "Permission change submitted for approval"
}
```

**Errors:** `400` · `401` · `403` · `404`

---

### GET /api/audit/events
Query the audit log.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR · **Scope:** Org-scoped  
**REQ:** REQ-044, REQ-045, REQ-046, REQ-047, REQ-064

**Query params:** `eventType` (LOGIN_SUCCESS | LOGIN_FAILURE | LOGOUT | EXPORT | PERMISSION_CHANGE | DATA_DELETE | DATA_ACCESS), `userId`, `startDate`, `endDate`, `page`, `size`

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "userId": "uuid",
      "eventType": "EXPORT",
      "entityType": "REPORT",
      "entityId": "uuid",
      "ipAddress": "192.168.1.10",
      "deviceFingerprint": "sha256hash",
      "details": { "reportType": "ENROLLMENTS" },
      "createdAt": "2024-01-15T10:00:00Z"
    }
  ],
  "totalElements": 234
}
```

**Errors:** `401` · `403`

---

### GET /api/admin/anomalies
List detected anomaly events.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-048, REQ-049, REQ-050, REQ-064

**Query params:** `type` (NEW_DEVICE | IP_OUT_OF_RANGE | EXPORT_BURST), `page`, `size`

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "userId": "uuid",
      "type": "NEW_DEVICE",
      "ipAddress": "10.0.0.15",
      "deviceFingerprint": "sha256hash",
      "details": { "userAgent": "Mozilla/5.0..." },
      "createdAt": "2024-01-15T09:55:00Z"
    }
  ]
}
```

**Errors:** `401` · `403`

---

### GET /api/approvals
List approval requests assigned to or created by the authenticated user.

**Auth required:** Yes · **Scope:** Assignee-scoped  
**REQ:** REQ-053, REQ-054, REQ-062

**Query params:** `status` (PENDING | APPROVED | REJECTED), `type`, `page`, `size`

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "type": "PERMISSION_CHANGE",
      "status": "PENDING",
      "requesterId": "uuid",
      "approverId": "uuid",
      "entityType": "USER_PERMISSION",
      "entityId": "uuid",
      "notes": null,
      "createdAt": "2024-01-15T10:00:00Z"
    }
  ]
}
```

**Errors:** `401`

---

### PUT /api/approvals/{id}/approve
Approve a pending request.

**Auth required:** Yes · **Object-level authz:** Assigned approver only  
**REQ:** REQ-053, REQ-054, REQ-062

**Request:**
```json
{
  "notes": "Approved after review"
}
```

**Response 200:**
```json
{
  "id": "uuid",
  "status": "APPROVED",
  "resolvedAt": "2024-01-15T11:00:00Z"
}
```

**Errors:** `401` · `403` · `404` · `409` already resolved

---

### PUT /api/approvals/{id}/reject
Reject a pending request.

**Auth required:** Yes · **Object-level authz:** Assigned approver only  
**REQ:** REQ-053, REQ-054, REQ-062

**Request:**
```json
{
  "notes": "Rejected: insufficient justification"
}
```

**Response 200:**
```json
{
  "id": "uuid",
  "status": "REJECTED",
  "resolvedAt": "2024-01-15T11:00:00Z"
}
```

**Errors:** `401` · `403` · `404` · `409` already resolved

---

## Phase 7 — Backup, DR & Administration

### POST /api/admin/backup/trigger
Manually trigger a backup job.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-055, REQ-056, REQ-064

**Request:**
```json
{
  "type": "FULL"
}
```
`type` ∈ {FULL, INCREMENTAL}

**Response 202:**
```json
{
  "jobId": "uuid",
  "type": "FULL",
  "status": "SCHEDULED",
  "message": "Backup job queued"
}
```

**Errors:** `400` invalid type · `401` · `403`

---

### GET /api/admin/backup/history
List backup run history.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-057, REQ-064

**Query params:** `type`, `page`, `size`

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "type": "FULL",
      "path": "/var/meridian/backups/full_20240115.dump.enc",
      "sizeBytes": 104857600,
      "retentionUntil": "2024-02-14T02:00:00Z",
      "createdAt": "2024-01-15T02:00:00Z"
    }
  ]
}
```

**Errors:** `401` · `403`

---

### GET /api/admin/recycle-bin
Browse soft-deleted records.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-059, REQ-064

**Query params:** `entityType` (USER | COURSE | ENROLLMENT), `page`, `size`

**Response 200:**
```json
{
  "content": [
    {
      "id": "uuid",
      "entityType": "COURSE",
      "entityId": "uuid",
      "deletedBy": "admin-uuid",
      "deletedAt": "2024-01-10T14:00:00Z",
      "expiresAt": "2024-01-24T14:00:00Z"
    }
  ]
}
```

**Errors:** `401` · `403`

---

### POST /api/admin/recycle-bin/{id}/restore
Restore a soft-deleted record.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-059, REQ-064

**Response 200:**
```json
{
  "id": "uuid",
  "entityType": "COURSE",
  "entityId": "uuid",
  "restoredAt": "2024-01-15T10:00:00Z"
}
```

**Errors:** `401` · `403` · `404` · `409` already expired / permanently purged

---

### DELETE /api/admin/recycle-bin/{id}
Permanently purge a record from the recycle bin before its expiry.

**Auth required:** Yes · **Role:** ROLE_ADMINISTRATOR  
**REQ:** REQ-059, REQ-064

**Response 204:** (no body)

**Errors:** `401` · `403` · `404`

---

## Endpoint Summary

| # | Method | Path | Auth | Object Authz | Phase |
|---|---|---|---|---|---|
| 1 | POST | /api/auth/register | No | — | 2 |
| 2 | POST | /api/auth/login | No | — | 2 |
| 3 | POST | /api/auth/logout | Yes | — | 2 |
| 4 | POST | /api/auth/refresh | No (cookie) | — | 2 |
| 5 | GET | /api/auth/me | Yes | Self | 2 |
| 6 | GET | /api/admin/users/pending | Yes (ADMIN) | — | 2 |
| 7 | PUT | /api/admin/users/{id}/approve | Yes (ADMIN) | — | 2 |
| 8 | PUT | /api/admin/users/{id}/reject | Yes (ADMIN) | — | 2 |
| 9 | GET | /api/admin/users | Yes (ADMIN) | — | 2 |
| 10 | PATCH | /api/admin/users/{id}/role | Yes (ADMIN) | — | 2 |
| 11 | GET | /api/courses | Yes | — | 2 |
| 12 | POST | /api/courses | Yes (ADMIN/FACULTY) | — | 2 |
| 13 | GET | /api/courses/{id} | Yes | — | 2 |
| 14 | PUT | /api/courses/{id} | Yes (ADMIN/FACULTY) | — | 2 |
| 15 | POST | /api/sessions | Yes | — | 3 |
| 16 | GET | /api/sessions | Yes | Student-scoped | 3 |
| 17 | GET | /api/sessions/{id} | Yes | Owner | 3 |
| 18 | PUT | /api/sessions/{id} | Yes | Owner | 3 |
| 19 | POST | /api/sessions/{id}/complete | Yes | Owner | 3 |
| 20 | GET | /api/sessions/{id}/activities | Yes | Owner | 3 |
| 21 | POST | /api/sessions/sync | Yes | Owner per record | 3 |
| 22 | GET | /api/analytics/mastery | Yes (MENTOR/ADMIN) | — | 4 |
| 23 | GET | /api/analytics/wrong-answers | Yes (MENTOR/ADMIN) | — | 4 |
| 24 | GET | /api/analytics/knowledge-gaps | Yes (MENTOR/ADMIN) | — | 4 |
| 25 | GET | /api/analytics/item-difficulty | Yes (MENTOR/ADMIN) | — | 4 |
| 26 | GET | /api/analytics/learner/{userId} | Yes (MENTOR/ADMIN) | Org-scoped | 4 |
| 27 | GET | /api/analytics/cohort/{cohortId} | Yes (MENTOR/ADMIN) | Org-scoped | 4 |
| 28 | GET | /api/analytics/course/{courseId} | Yes (MENTOR/ADMIN) | Org-scoped | 4 |
| 29 | GET | /api/reports/enrollments | Yes | Org-scoped | 5 |
| 30 | GET | /api/reports/seat-utilization | Yes | Org-scoped | 5 |
| 31 | GET | /api/reports/refunds | Yes | Org-scoped | 5 |
| 32 | GET | /api/reports/inventory | Yes | Org-scoped | 5 |
| 33 | GET | /api/reports/certifications/expiring | Yes | Org-scoped | 5 |
| 34 | POST | /api/reports/schedules | Yes (ADMIN/MENTOR) | — | 5 |
| 35 | GET | /api/reports/schedules | Yes | Org-scoped | 5 |
| 36 | DELETE | /api/reports/schedules/{id} | Yes | Owner | 5 |
| 37 | POST | /api/reports/export | Yes | Org-scoped | 5 |
| 38 | GET | /api/notifications | Yes | Recipient | 5 |
| 39 | PUT | /api/notifications/{id}/read | Yes | Recipient | 5 |
| 40 | GET | /api/notifications/stream | Yes | Recipient | 5 |
| 41 | GET | /api/governance/users/{id}/permissions | Yes | ADMIN or self | 6 |
| 42 | PUT | /api/governance/users/{id}/permissions | Yes (ADMIN) | — | 6 |
| 43 | GET | /api/audit/events | Yes (ADMIN) | Org-scoped | 6 |
| 44 | GET | /api/admin/anomalies | Yes (ADMIN) | — | 6 |
| 45 | GET | /api/approvals | Yes | Assignee-scoped | 6 |
| 46 | PUT | /api/approvals/{id}/approve | Yes | Assignee | 6 |
| 47 | PUT | /api/approvals/{id}/reject | Yes | Assignee | 6 |
| 48 | POST | /api/admin/backup/trigger | Yes (ADMIN) | — | 7 |
| 49 | GET | /api/admin/backup/history | Yes (ADMIN) | — | 7 |
| 50 | GET | /api/admin/recycle-bin | Yes (ADMIN) | — | 7 |
| 51 | POST | /api/admin/recycle-bin/{id}/restore | Yes (ADMIN) | — | 7 |
| 52 | DELETE | /api/admin/recycle-bin/{id} | Yes (ADMIN) | — | 7 |

**Total: 52 endpoints · 50 require auth · 28 require object-level or org-scoped authorization**
