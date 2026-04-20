# Test Coverage Audit

## Scope and Method
- Audit mode: static inspection only (no code/test/script/container execution).
- Inspected only: backend endpoint declarations, API/unit/E2E test sources, `run_tests.sh`, and `README.md`.
- Evidence basis:
  - Endpoints: Spring controller annotations in `server/src/main/java/**`.
  - API tests: `api_tests/src/test/java/com/meridian/*.java`, `e2e_tests/tests/*.spec.ts`, `e2e_tests/tests/helpers.ts`.
  - Unit tests: `unit_tests/server/*.java`, `unit_tests/web/*.spec.ts`.

## Project Type Detection
- README top does **not** explicitly declare one of: `backend | fullstack | web | android | ios | desktop`.
- Inferred project type: **fullstack**.
- Evidence: `README.md` describes Angular SPA + Spring Boot API; repo contains both `server/` and `web/`.

## Backend Endpoint Inventory
Total endpoints found: **76**

1. `GET /api/v1/health`
2. `POST /api/v1/auth/register`
3. `POST /api/v1/auth/login`
4. `POST /api/v1/auth/refresh`
5. `POST /api/v1/auth/logout`
6. `GET /api/v1/users/me`
7. `GET /api/v1/admin/users`
8. `POST /api/v1/admin/users/{id}/approve`
9. `POST /api/v1/admin/users/{id}/reject`
10. `GET /api/v1/admin/users/{id}`
11. `PATCH /api/v1/admin/users/{id}/status`
12. `POST /api/v1/admin/users/{id}/unlock`
13. `GET /api/v1/sessions`
14. `POST /api/v1/sessions`
15. `PATCH /api/v1/sessions/{id}`
16. `POST /api/v1/sessions/{id}/pause`
17. `POST /api/v1/sessions/{id}/continue`
18. `POST /api/v1/sessions/{id}/complete`
19. `POST /api/v1/sessions/{id}/sets`
20. `PATCH /api/v1/sessions/{id}/sets/{setId}`
21. `GET /api/v1/sessions/{id}`
22. `POST /api/v1/sessions/attempt-drafts`
23. `GET /api/v1/sessions/{sessionId}/attempt-drafts`
24. `DELETE /api/v1/sessions/{sessionId}/attempt-drafts`
25. `POST /api/v1/sessions/{sessionId}/submit-attempts`
26. `POST /api/v1/sessions/sync`
27. `GET /api/v1/reports`
28. `POST /api/v1/reports`
29. `GET /api/v1/reports/{id}`
30. `GET /api/v1/reports/{id}/download`
31. `POST /api/v1/reports/{id}/cancel`
32. `GET /api/v1/reports/schedules`
33. `POST /api/v1/reports/schedules`
34. `PUT /api/v1/reports/schedules/{id}`
35. `DELETE /api/v1/reports/schedules/{id}`
36. `GET /api/v1/courses`
37. `POST /api/v1/courses`
38. `PUT /api/v1/courses/{id}`
39. `DELETE /api/v1/courses/{id}`
40. `GET /api/v1/courses/{id}/cohorts`
41. `GET /api/v1/courses/{id}/assessment-items`
42. `GET /api/v1/courses/{courseId}/activities`
43. `POST /api/v1/courses/{courseId}/activities`
44. `GET /api/v1/courses/{courseId}/knowledge-points`
45. `POST /api/v1/courses/{courseId}/knowledge-points`
46. `POST /api/v1/assessment-items`
47. `PUT /api/v1/assessment-items/{id}`
48. `GET /api/v1/analytics/mastery-trends`
49. `GET /api/v1/analytics/wrong-answers`
50. `GET /api/v1/analytics/weak-knowledge-points`
51. `GET /api/v1/analytics/item-stats`
52. `GET /api/v1/notifications`
53. `POST /api/v1/notifications/{id}/read`
54. `POST /api/v1/notifications/read-all`
55. `GET /api/v1/notifications/unread-count`
56. `GET /api/v1/admin/audit`
57. `GET /api/v1/admin/anomalies`
58. `POST /api/v1/admin/anomalies/{id}/resolve`
59. `GET /api/v1/admin/approvals`
60. `POST /api/v1/admin/approvals/{id}/approve`
61. `POST /api/v1/admin/approvals/{id}/reject`
62. `GET /api/v1/admin/notification-templates`
63. `PUT /api/v1/admin/notification-templates/{key}`
64. `GET /api/v1/admin/backups`
65. `POST /api/v1/admin/backups/run`
66. `GET /api/v1/admin/backups/policy`
67. `PUT /api/v1/admin/backups/policy`
68. `POST /api/v1/admin/backups/recovery-drill`
69. `GET /api/v1/admin/backups/recovery-drills`
70. `GET /api/v1/admin/allowed-ip-ranges`
71. `POST /api/v1/admin/allowed-ip-ranges`
72. `DELETE /api/v1/admin/allowed-ip-ranges/{id}`
73. `GET /api/v1/admin/recycle-bin/policy`
74. `GET /api/v1/admin/recycle-bin`
75. `POST /api/v1/admin/recycle-bin/{type}/{id}/restore`
76. `DELETE /api/v1/admin/recycle-bin/{type}/{id}`

Endpoint evidence: class + mapping lines from controller files, e.g. `server/src/main/java/com/meridian/auth/AuthController.java:12,18,23,29,34`, `server/src/main/java/com/meridian/reports/ReportController.java:41,62,105,114,135,148,161,173,191,208`.

## API Test Mapping Table
Legend:
- `TNM` = true no-mock HTTP
- `HWM` = HTTP with mocking
- `UNIT` = unit-only/indirect

| Endpoint | Covered | Test type | Test files | Evidence |
|---|---|---|---|---|
| GET /api/v1/health | No | UNIT | - | No request hit found in API/E2E tests |
| POST /api/v1/auth/register | Yes | HWM | AuthApiTest.java | `registerNewStudent_returns201`, line 41 |
| POST /api/v1/auth/login | Yes | HWM + TNM | AuthApiTest.java; AuthAuditTest.java; helpers.ts; 05-new-device-anomaly.spec.ts | `loginAdmin_returns200WithTokens` line 111; `apiLogin()` line 6 |
| POST /api/v1/auth/refresh | Yes | HWM | AuthApiTest.java | `refreshToken_returns200`, `refreshTokenReuse_returns401` |
| POST /api/v1/auth/logout | Yes | HWM | AuthApiTest.java | `logout_returns204` line 199 |
| GET /api/v1/users/me | No | UNIT | - | No request hit |
| GET /api/v1/admin/users | Yes | HWM + TNM | OrgScopeApiTest.java; 01-register-approve-login.spec.ts; 06-org-scope-isolation.spec.ts | `unauthenticated_adminUsersDenied`; request.get lines 29/31/35 |
| POST /api/v1/admin/users/{id}/approve | Yes | TNM | helpers.ts; 01-register-approve-login.spec.ts | `adminApproveUser()` line 13 |
| POST /api/v1/admin/users/{id}/reject | No | UNIT | - | No request hit |
| GET /api/v1/admin/users/{id} | Yes | TNM | 01-register-approve-login.spec.ts | request.get line 41 |
| PATCH /api/v1/admin/users/{id}/status | No | UNIT | - | No request hit |
| POST /api/v1/admin/users/{id}/unlock | No | UNIT | - | No request hit |
| GET /api/v1/sessions | Yes | HWM + TNM | OrgScopeApiTest.java; 06-org-scope-isolation.spec.ts | `student_cannotListOtherStudentSessions`; request.get line 48 |
| POST /api/v1/sessions | Yes | HWM | OrgScopeApiTest.java | `facultyMentor_cannotCreateSession` |
| PATCH /api/v1/sessions/{id} | Yes | HWM | OrgScopeApiTest.java | `facultyMentor_cannotPatchSession` |
| POST /api/v1/sessions/{id}/pause | No | UNIT | - | No request hit |
| POST /api/v1/sessions/{id}/continue | No | UNIT | - | No request hit |
| POST /api/v1/sessions/{id}/complete | No | UNIT | - | No request hit |
| POST /api/v1/sessions/{id}/sets | No | UNIT | - | No request hit |
| PATCH /api/v1/sessions/{id}/sets/{setId} | No | UNIT | - | No request hit |
| GET /api/v1/sessions/{id} | Yes | TNM | 02-offline-session.spec.ts | request.get line 54 |
| POST /api/v1/sessions/attempt-drafts | No | UNIT | - | No request hit |
| GET /api/v1/sessions/{sessionId}/attempt-drafts | No | UNIT | - | No request hit |
| DELETE /api/v1/sessions/{sessionId}/attempt-drafts | No | UNIT | - | No request hit |
| POST /api/v1/sessions/{sessionId}/submit-attempts | No | UNIT | - | No request hit |
| POST /api/v1/sessions/sync | Yes | HWM | SyncApiTest.java; OrgScopeApiTest.java | `syncNewSession_returnsCreated`; `facultyMentor_cannotSyncSessions` |
| GET /api/v1/reports | Yes | HWM | ReportApiTest.java | `listReports_returnsOnlyOwnRuns` |
| POST /api/v1/reports | Yes | HWM + TNM | ReportApiTest.java; 04-export-notification.spec.ts | `createReport_sendKindField...`; request.post line 16 |
| GET /api/v1/reports/{id} | Yes | HWM | ReportApiTest.java | `getReport_ownerCanAccess_returnsRun` |
| GET /api/v1/reports/{id}/download | Yes | HWM | ReportApiTest.java | `downloadReport_notReady_returns409` |
| POST /api/v1/reports/{id}/cancel | Yes | HWM | ReportApiTest.java | `cancelReport_otherUserDenied_returns403` |
| GET /api/v1/reports/schedules | No | UNIT | - | No request hit |
| POST /api/v1/reports/schedules | Yes | HWM | ReportApiTest.java | `createSchedule_returnsScheduleDto` |
| PUT /api/v1/reports/schedules/{id} | Yes | HWM | ReportApiTest.java | `updateSchedule_toggleEnabled_returns200` |
| DELETE /api/v1/reports/schedules/{id} | No | UNIT | - | No request hit |
| GET /api/v1/courses | Yes | TNM | 07-recycle-bin.spec.ts | request.get line 65 |
| POST /api/v1/courses | Yes | TNM | 07-recycle-bin.spec.ts | request.post line 25 |
| PUT /api/v1/courses/{id} | No | UNIT | - | No request hit |
| DELETE /api/v1/courses/{id} | Yes | TNM | 07-recycle-bin.spec.ts | request.delete line 41 |
| GET /api/v1/courses/{id}/cohorts | Yes | HWM | ClassificationApiTest.java | `student_canReadPublicCourseCohorts` |
| GET /api/v1/courses/{id}/assessment-items | Yes | HWM | ClassificationApiTest.java | `student_cannotReadConfidentialCourseItems` |
| GET /api/v1/courses/{courseId}/activities | Yes | HWM | ClassificationApiTest.java | `student_cannotReadConfidentialCourseActivities` |
| POST /api/v1/courses/{courseId}/activities | No | UNIT | - | No request hit |
| GET /api/v1/courses/{courseId}/knowledge-points | Yes | HWM | ClassificationApiTest.java | `student_cannotReadConfidentialCourseKnowledgePoints` |
| POST /api/v1/courses/{courseId}/knowledge-points | No | UNIT | - | No request hit |
| POST /api/v1/assessment-items | No | UNIT | - | No request hit |
| PUT /api/v1/assessment-items/{id} | No | UNIT | - | No request hit |
| GET /api/v1/analytics/mastery-trends | Yes | TNM | 06-org-scope-isolation.spec.ts | request.get lines 57/66/75 |
| GET /api/v1/analytics/wrong-answers | No | UNIT | - | No request hit |
| GET /api/v1/analytics/weak-knowledge-points | No | UNIT | - | No request hit |
| GET /api/v1/analytics/item-stats | No | UNIT | - | No request hit |
| GET /api/v1/notifications | Yes | TNM | 05-new-device-anomaly.spec.ts | request.get line 40 |
| POST /api/v1/notifications/{id}/read | No | UNIT | - | UI click in 04-export-notification.spec.ts does not explicitly send exact method+path |
| POST /api/v1/notifications/read-all | No | UNIT | - | No explicit request hit |
| GET /api/v1/notifications/unread-count | No | UNIT | - | No explicit request hit |
| GET /api/v1/admin/audit | Yes | HWM | OrgScopeApiTest.java; AuthAuditTest.java | `student_cannotAccessAdminAudit`; `auditEndpoint_authorizedAdminReturns200` |
| GET /api/v1/admin/anomalies | Yes | TNM | 05-new-device-anomaly.spec.ts | request.get line 31 |
| POST /api/v1/admin/anomalies/{id}/resolve | No | UNIT | - | No request hit |
| GET /api/v1/admin/approvals | Yes | HWM | OrgScopeApiTest.java | `student_cannotAccessAdminApprovals` |
| POST /api/v1/admin/approvals/{id}/approve | No | UNIT | - | No request hit |
| POST /api/v1/admin/approvals/{id}/reject | No | UNIT | - | No request hit |
| GET /api/v1/admin/notification-templates | No | UNIT | - | No request hit |
| PUT /api/v1/admin/notification-templates/{key} | No | UNIT | - | No request hit |
| GET /api/v1/admin/backups | Yes | HWM | OrgScopeApiTest.java | `student_cannotAccessAdminBackups` |
| POST /api/v1/admin/backups/run | No | UNIT | - | No request hit |
| GET /api/v1/admin/backups/policy | No | UNIT | - | No request hit |
| PUT /api/v1/admin/backups/policy | No | UNIT | - | No request hit |
| POST /api/v1/admin/backups/recovery-drill | No | UNIT | - | No request hit |
| GET /api/v1/admin/backups/recovery-drills | No | UNIT | - | No request hit |
| GET /api/v1/admin/allowed-ip-ranges | No | UNIT | - | No request hit |
| POST /api/v1/admin/allowed-ip-ranges | No | UNIT | - | No request hit |
| DELETE /api/v1/admin/allowed-ip-ranges/{id} | No | UNIT | - | No request hit |
| GET /api/v1/admin/recycle-bin/policy | No | UNIT | - | No request hit |
| GET /api/v1/admin/recycle-bin | No | UNIT | - | No explicit API request (only UI navigation in 07-recycle-bin.spec.ts) |
| POST /api/v1/admin/recycle-bin/{type}/{id}/restore | Yes | TNM | 07-recycle-bin.spec.ts | request.post line 57 (`courses` concrete type) |
| DELETE /api/v1/admin/recycle-bin/{type}/{id} | No | UNIT | - | No request hit |

## API Test Classification

### True No-Mock HTTP
- `e2e_tests/tests/*.spec.ts` requests using Playwright `request.get/post/delete` to `http://localhost:8080` (`e2e_tests/tests/helpers.ts:3-14`).
- Includes endpoints like `/api/v1/auth/login`, `/api/v1/admin/users`, `/api/v1/courses`, `/api/v1/analytics/mastery-trends`.
- Real HTTP transport is used when executed (external server URL), no in-test service/controller stubs observed.

### HTTP with Mocking
- All `api_tests` classes are `@SpringBootTest + @AutoConfigureMockMvc` and use `MockMvc` (`AuthApiTest.java:24-33`, `SyncApiTest.java:21-29`, etc.).
- `MockMvc` is HTTP-like but not real network transport.
- Auth context is explicitly mocked in many tests via `@WithMockUser` (`SyncApiTest.java:36+`, `OrgScopeApiTest.java:35+`, `ReportApiTest.java:43+`, `ClassificationApiTest.java:38+`) and `SecurityMockMvcRequestPostProcessors.user(...)` (`AuthAuditTest.java:52-54`, `74-76`).

### Non-HTTP (Unit/Indirect)
- Backend unit tests: `unit_tests/server/*.java`.
- Frontend unit tests: `unit_tests/web/*.spec.ts`.

## Mock Detection
- Mocked security principal:
  - `@WithMockUser` in `api_tests/src/test/java/com/meridian/SyncApiTest.java`, `OrgScopeApiTest.java`, `ReportApiTest.java`, `ClassificationApiTest.java`, `AuthAuditTest.java`.
- Mocked request user processor:
  - `SecurityMockMvcRequestPostProcessors.user(...)` in `api_tests/src/test/java/com/meridian/AuthAuditTest.java:52-54,74-76`.
- Mocked dependencies in backend units:
  - `Mockito.mock(...)` in `unit_tests/server/AuthServiceAuditTest.java`, `IdempotencyServiceTest.java`, `SyncResolverTest.java`.
- Mocked frontend dependencies:
  - `spyOn(authStore, ...)` in `unit_tests/web/auth.guard.spec.ts:22,29,38`.
  - `HttpClientTestingModule` + `HttpTestingController` in `unit_tests/web/outbox.service.spec.ts`.

## Coverage Summary
- Total endpoints: **76**
- Endpoints with HTTP tests (TNM + HWM): **33**
- Endpoints with true no-mock tests: **14**
- HTTP coverage: **43.42%** (`33/76`)
- True API coverage: **18.42%** (`14/76`)

## Unit Test Analysis

### Backend Unit Tests
Files:
- `unit_tests/server/PasswordPolicyTest.java`
- `unit_tests/server/LockoutPolicyTest.java`
- `unit_tests/server/JwtServiceTest.java`
- `unit_tests/server/IdempotencyServiceTest.java`
- `unit_tests/server/SyncResolverTest.java`
- `unit_tests/server/AuthServiceAuditTest.java`
- `unit_tests/server/AesAttributeConverterTest.java`
- `unit_tests/server/TemplateRendererTest.java`

Modules covered:
- Controllers: **none** (no direct controller unit tests).
- Services:
  - `AuthService` (`AuthServiceAuditTest`)
  - `IdempotencyService` (`IdempotencyServiceTest`)
  - `SyncResolver` (`SyncResolverTest`)
  - `JwtService` (`JwtServiceTest`)
- Repositories: mocked only as collaborators (not repository behavior tests).
- Auth/guards/middleware:
  - `PasswordPolicy`, `LockoutPolicy` covered.
  - No explicit test for backend filters/interceptors/security config classes.

Important backend modules not tested (from endpoint surface):
- Controllers with zero direct test hits: `AllowedIpRangeController`, `TemplateController`, large parts of `BackupController`, `ApprovalController`, `AttemptDraftController`, `AssessmentItemController`, `NotificationController` (read operations only partially covered), `AnalyticsController` (3/4 endpoints uncovered), `HealthController`, `UserController`.

### Frontend Unit Tests (STRICT REQUIREMENT)
Frontend unit tests: **PRESENT**

Evidence for strict detection rules:
- Identifiable frontend test files exist:
  - `unit_tests/web/auth.guard.spec.ts`
  - `unit_tests/web/auth.store.spec.ts`
  - `unit_tests/web/outbox.service.spec.ts`
  - `unit_tests/web/pending-route.spec.ts`
  - `unit_tests/web/session-sync-keys.spec.ts`
- Tests target frontend logic/components/modules:
  - `AuthStore`, `authGuard`, `OutboxService`, `SessionStore`, route config in `app.routes`.
- Framework/tools evident:
  - Jasmine style `describe/it/expect` in files above.
  - Angular `TestBed` / `RouterTestingModule` / `HttpClientTestingModule` imports.
- Tests import frontend modules directly:
  - `../../web/src/app/core/stores/auth.store`
  - `../../web/src/app/core/guards/auth.guard`
  - `../../web/src/app/sessions/session.store`
  - `../../web/src/app/app.routes`

Important frontend components/modules not tested:
- Auth pages/components: `web/src/app/auth/pages/login.component.ts`, `register.component.ts`, `pending.component.ts`.
- Session UI pages: `web/src/app/sessions/pages/session-new.component.ts`, `session-run.component.ts`, `sessions-list.component.ts`.
- Admin pages: `web/src/app/admin/pages/*` (users, approvals, audit, backups, ip ranges, templates, anomalies, recycle-bin).
- Analytics/report pages: `web/src/app/analytics/pages/*`, `web/src/app/reports/pages/*`.
- Notification UI page: `web/src/app/notifications/pages/inbox.component.ts`.

Mandatory strict verdict for frontend adequacy:
- Frontend unit tests are present but **insufficient breadth** for a fullstack app with broad UI surface.
- **CRITICAL GAP** flagged (strict mode): frontend testing is narrow and backend/API-focused coverage dominates.

### Cross-Layer Observation
- Backend/API tests significantly outnumber frontend unit tests in breadth of feature surface.
- Balance is weak: frontend has only core-state/guard/service tests, while many UI behaviors rely primarily on E2E paths.

## API Observability Check
Strengths:
- Most `api_tests` explicitly show method/path, request body/headers, and response assertions (status + JSON path), e.g. `AuthApiTest`, `ReportApiTest`, `SyncApiTest`.

Weaknesses:
- Several tests assert only status or array existence without validating payload semantics, e.g. `OrgScopeApiTest.student_cannotListOtherStudentSessions` and some E2E API checks.
- UI-driven E2E steps do not always expose exact API request/response details (e.g. notification read action by click).

## Test Quality and Sufficiency
- Success paths: present for auth, report creation/listing, sync create/update, course create/delete/restore.
- Failure/negative paths: present for auth failures, role boundaries, forbidden access, idempotency conflicts.
- Edge/validation: some coverage (weak password, duplicate username, lockout, idempotency mismatch).
- Auth/permissions: good presence in API tests but heavily reliant on mocked security context (`@WithMockUser`).
- Integration boundaries: partial; many domain areas have no endpoint tests at all.
- Assertion depth: mixed; some tests are deep, others superficial.

`run_tests.sh` strict check:
- Docker-based execution exists for app runtime setup (`docker compose` in README).
- Test runner is **not Docker-contained** and relies on local runtime tools (`/usr/libexec/java_home`, `./mvnw`, local `node_modules`, `npm ci`, `npx playwright`) in `run_tests.sh`.
- Strict outcome: **FLAG (local dependency required).**

## End-to-End Expectations (Fullstack)
- Real FE↔BE E2E tests are present (`e2e_tests/tests/*.spec.ts`), but they cover selected flows only.
- Missing broad FE↔BE end-to-end coverage for many admin/analytics/reporting/settings paths.
- Existing API + unit tests partially compensate for backend logic, but do **not** compensate for limited frontend unit breadth.

## Tests Check
- Endpoint inventory: complete from static controller annotations.
- API mapping: complete for all 76 endpoints.
- Mocking classification: complete with concrete locations.
- Frontend unit strict verification: complete and explicit.

## Test Coverage Score
- **Score: 46 / 100** — Partial Pass

### Score Rationale
- Large endpoint surface uncovered (43 endpoints without explicit HTTP hit).
- True no-mock API coverage is low (18.42%).
- Heavy dependence on `MockMvc` + mocked auth principals for API suite.
- Frontend unit tests exist but are narrow for fullstack scope.
- Positive factors: solid auth/sync/report-focused assertions and useful negative/security cases.

## Key Gaps
1. 43/76 endpoints have no explicit HTTP test coverage.
2. Low true no-mock API coverage (14 endpoints).
3. No explicit coverage for health, user profile, attempts endpoints, many admin config endpoints, most analytics endpoints.
4. Frontend unit coverage is limited to core infra logic; most UI pages/components untested.
5. `run_tests.sh` requires local Java/Node tooling and performs runtime installs (`npm ci`) outside Docker-contained flow.

## Confidence and Assumptions
- Confidence: **High** for endpoint inventory and explicit request-hit mapping.
- Confidence: **Medium** for inference that E2E requests are true no-mock at runtime (execution not performed).
- Assumption: only explicit method+path calls in test source count as endpoint coverage (UI clicks without explicit API call were not credited).

---

# README Audit

## README Location Check
- Required path exists: `README.md` at repo root.

## Hard Gate Evaluation

### 1) Formatting
- Result: **PASS**
- Evidence: structured Markdown headings/tables/code blocks in `README.md`.

### 2) Startup Instructions (backend/fullstack requirement)
- Result: **PASS**
- Evidence:
  - Quickstart includes `docker compose up --build -d` (`README.md`, Quickstart section).

### 3) Access Method
- Result: **PASS**
- Evidence:
  - URLs listed: `https://localhost/`, `https://localhost/api/v1/health`.
  - Port context included in Architecture block (`nginx HTTPS :443`, server `:8080`).

### 4) Verification Method
- Result: **PASS**
- Evidence:
  - “Verifying It Works” section added with copy-pasteable `curl` commands: health check, login (with correct `deviceFingerprint` field), and `/api/v1/users/me` token verification.
  - UI smoke-check walkthrough included (open URL → login → confirm dashboard).

### 5) Environment Rules (strict no runtime installs/manual setup)
- Result: **PASS**
- Evidence:
  - Cert generation replaced with Docker one-liner (`docker run --rm alpine sh -c “apk add -q openssl && openssl req ...”`); no host `openssl` required.
  - “Running the Tests” section now leads with Docker commands (`docker compose run --rm server ./mvnw test`, `docker run node:20-alpine`, `docker run mcr.microsoft.com/playwright`); local `run_tests.sh` demoted to a secondary convenience note.

### 6) Demo Credentials (conditional)
- Result: **PASS**
- Evidence:
  - Auth clearly exists.
  - Credentials section includes username/password and roles (Administrator, Student, Corporate Mentor, Faculty Mentor).

### 7) Project Type Declaration at Top
- Result: **PASS**
- Evidence:
  - `**Project type:** fullstack` added as the first content line after the title.

## Engineering Quality Review

### High Priority Issues
- None remaining. All three previously failing hard gates resolved.

### Medium Priority Issues
1. No explicit role-to-flow mapping for credentials (which role validates which path).

### Low Priority Issues
1. README is detailed; quick operational verification is now prominent with the new "Verifying It Works" section.

## Hard Gate Failures
- None. All hard gates now pass.

## README Verdict
- **PASS**

## Final Verdicts
- Test Coverage Audit Verdict: **Partial Pass** (43% HTTP endpoint coverage, 18% true no-mock; solid auth/sync/report assertions and negative-path cases present, but 43/76 endpoints have no explicit HTTP test)
- README Audit Verdict: **PASS**
