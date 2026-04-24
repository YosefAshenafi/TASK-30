# Test Coverage Audit

## Scope and Method
- Mode: static inspection only (no test execution, no builds, no package install commands run).
- Project type detection: declared as `fullstack` in `README.md` (`README.md:3`).
- Assumption: `api_tests/` and `unit_tests/` are archival mirrors, while authoritative backend tests are under `server/src/test/java` (`README.md:177-179`, `README.md:237`).

## Backend Endpoint Inventory
Resolved from Spring controller mappings under `server/src/main/java/com/meridian/**`.

| # | Method | Path |
|---|---|---|
| 1 | GET | /api/v1/health |
| 2 | POST | /api/v1/auth/register |
| 3 | POST | /api/v1/auth/login |
| 4 | POST | /api/v1/auth/refresh |
| 5 | POST | /api/v1/auth/logout |
| 6 | GET | /api/v1/users/me |
| 7 | GET | /api/v1/admin/users |
| 8 | POST | /api/v1/admin/users/:id/approve |
| 9 | POST | /api/v1/admin/users/:id/reject |
| 10 | GET | /api/v1/admin/users/:id |
| 11 | PATCH | /api/v1/admin/users/:id/status |
| 12 | POST | /api/v1/admin/users/:id/unlock |
| 13 | GET | /api/v1/admin/audit |
| 14 | GET | /api/v1/admin/approvals |
| 15 | POST | /api/v1/admin/approvals/:id/approve |
| 16 | POST | /api/v1/admin/approvals/:id/reject |
| 17 | GET | /api/v1/admin/allowed-ip-ranges |
| 18 | POST | /api/v1/admin/allowed-ip-ranges |
| 19 | DELETE | /api/v1/admin/allowed-ip-ranges/:id |
| 20 | GET | /api/v1/admin/anomalies |
| 21 | POST | /api/v1/admin/anomalies/:id/resolve |
| 22 | GET | /api/v1/admin/backups |
| 23 | POST | /api/v1/admin/backups/run |
| 24 | GET | /api/v1/admin/backups/policy |
| 25 | PUT | /api/v1/admin/backups/policy |
| 26 | POST | /api/v1/admin/backups/recovery-drill |
| 27 | GET | /api/v1/admin/backups/recovery-drills |
| 28 | GET | /api/v1/admin/recycle-bin/policy |
| 29 | GET | /api/v1/admin/recycle-bin |
| 30 | POST | /api/v1/admin/recycle-bin/:type/:id/restore |
| 31 | DELETE | /api/v1/admin/recycle-bin/:type/:id |
| 32 | GET | /api/v1/admin/notification-templates |
| 33 | PUT | /api/v1/admin/notification-templates/:key |
| 34 | GET | /api/v1/notifications |
| 35 | POST | /api/v1/notifications/:id/read |
| 36 | POST | /api/v1/notifications/read-all |
| 37 | GET | /api/v1/notifications/unread-count |
| 38 | POST | /api/v1/reports |
| 39 | GET | /api/v1/reports/:id |
| 40 | GET | /api/v1/reports/:id/download |
| 41 | GET | /api/v1/reports |
| 42 | POST | /api/v1/reports/:id/cancel |
| 43 | GET | /api/v1/reports/schedules |
| 44 | POST | /api/v1/reports/schedules |
| 45 | PUT | /api/v1/reports/schedules/:id |
| 46 | DELETE | /api/v1/reports/schedules/:id |
| 47 | GET | /api/v1/courses |
| 48 | POST | /api/v1/courses |
| 49 | PUT | /api/v1/courses/:id |
| 50 | DELETE | /api/v1/courses/:id |
| 51 | GET | /api/v1/courses/:id/cohorts |
| 52 | GET | /api/v1/courses/:id/assessment-items |
| 53 | GET | /api/v1/courses/:courseId/knowledge-points |
| 54 | POST | /api/v1/courses/:courseId/knowledge-points |
| 55 | GET | /api/v1/courses/:courseId/activities |
| 56 | POST | /api/v1/courses/:courseId/activities |
| 57 | POST | /api/v1/assessment-items |
| 58 | PUT | /api/v1/assessment-items/:id |
| 59 | GET | /api/v1/analytics/mastery-trends |
| 60 | GET | /api/v1/analytics/wrong-answers |
| 61 | GET | /api/v1/analytics/weak-knowledge-points |
| 62 | GET | /api/v1/analytics/item-stats |
| 63 | POST | /api/v1/sessions |
| 64 | PATCH | /api/v1/sessions/:id |
| 65 | POST | /api/v1/sessions/:id/pause |
| 66 | POST | /api/v1/sessions/:id/continue |
| 67 | POST | /api/v1/sessions/:id/complete |
| 68 | POST | /api/v1/sessions/:id/sets |
| 69 | PATCH | /api/v1/sessions/:id/sets/:setId |
| 70 | GET | /api/v1/sessions/:id |
| 71 | GET | /api/v1/sessions |
| 72 | POST | /api/v1/sessions/sync |
| 73 | POST | /api/v1/sessions/attempt-drafts |
| 74 | GET | /api/v1/sessions/:sessionId/attempt-drafts |
| 75 | DELETE | /api/v1/sessions/:sessionId/attempt-drafts |
| 76 | POST | /api/v1/sessions/:sessionId/submit-attempts |

Evidence for route declarations: `@RequestMapping`/`@*Mapping` in controller files (for example `server/src/main/java/com/meridian/auth/AuthController.java:12-35`, plus `rg` extraction across controllers).

## API Test Mapping Table
Coverage criterion used: exact `METHOD + PATH` hit in test request.

| Endpoint | Covered | Test Type | Test Files | Evidence |
|---|---|---|---|---|
| GET /api/v1/health | yes | true no-mock HTTP | `server/src/test/java/com/meridian/TrueNoMockHttpApiTest.java` | `health_overRealHttp_returnsUpWithVersion` (`TrueNoMockHttpApiTest.java:100-107`) |
| POST /api/v1/auth/register | yes | true no-mock HTTP | same | `authRegister_newStudent_returnsPendingResponse` (`:162-181`) |
| POST /api/v1/auth/login | yes | true no-mock HTTP | same | `authLogin_withSeededAdmin_returnsAccessAndRefreshTokens` (`:113-126`) |
| POST /api/v1/auth/refresh | yes | true no-mock HTTP | same | `authRefresh_withValidRefreshToken_returnsNewAccessToken` (`:186-199`) |
| POST /api/v1/auth/logout | yes | true no-mock HTTP | same | `authLogout_withValidRefreshToken_returns204` (`:204-214`) |
| GET /api/v1/users/me | yes | true no-mock HTTP | same | `usersMe_withValidStudentJwt_returnsProfile` (`:221-235`) |
| GET /api/v1/admin/users | yes | true no-mock HTTP | same | `adminUsers_withAdminJwt_returnsPageShape` (`:277+`) |
| POST /api/v1/admin/users/:id/approve | yes | true no-mock HTTP | same | `adminUsersApprove_unknownId_withAdminJwt_returns4xx` (`:962+`) |
| POST /api/v1/admin/users/:id/reject | yes | true no-mock HTTP | same | `adminUsersReject_emptyReason_withAdminJwt_returns400` (`:986+`) |
| GET /api/v1/admin/users/:id | yes | true no-mock HTTP | same | `adminUsersById_withAdminJwt_returns200Or404` (`:944+`) |
| PATCH /api/v1/admin/users/:id/status | yes | true no-mock HTTP | same | `adminUsersPatchStatus_invalidValue_returns400` (`:1035+`) |
| POST /api/v1/admin/users/:id/unlock | yes | true no-mock HTTP | same | `adminUsersUnlock_withAdminJwt_idempotentlyReturns2xx` (`:1010+`) |
| GET /api/v1/admin/audit | yes | true no-mock HTTP | same | `adminAudit_withAdminJwt_returnsPageShape` (`:295+`) |
| GET /api/v1/admin/approvals | yes | true no-mock HTTP | same | `adminApprovals_withAdminJwt_returnsPage` (`:328+`) |
| POST /api/v1/admin/approvals/:id/approve | yes | true no-mock HTTP | same | `approvalsApprove_unknownId_withAdminJwt_returns4xx` (`:1173+`) |
| POST /api/v1/admin/approvals/:id/reject | yes | true no-mock HTTP | same | `approvalsReject_withStudentJwt_returns403` (`:1197+`) |
| GET /api/v1/admin/allowed-ip-ranges | yes | true no-mock HTTP | same | `allowedIpRanges_fullCrud_overRealHttp` (`:1061+`) |
| POST /api/v1/admin/allowed-ip-ranges | yes | true no-mock HTTP | same | `allowedIpRanges_fullCrud_overRealHttp` (`:1061+`) |
| DELETE /api/v1/admin/allowed-ip-ranges/:id | yes | true no-mock HTTP | same | `allowedIpRanges_fullCrud_overRealHttp` (`:1061+`) |
| GET /api/v1/admin/anomalies | yes | true no-mock HTTP | same | `adminAnomalies_withAdminJwt_returnsPage` (`:358+`) |
| POST /api/v1/admin/anomalies/:id/resolve | yes | true no-mock HTTP | same | `anomaliesResolve_unknownId_returns404` (`:1147+`) |
| GET /api/v1/admin/backups | yes | true no-mock HTTP | same | `adminBackups_list_withAdminJwt_returnsPageShape` (`:1797+`) |
| POST /api/v1/admin/backups/run | yes | true no-mock HTTP | same | `adminBackups_run_withAdminJwt_triggersBackupOrAccepts409` (`:1812+`) |
| GET /api/v1/admin/backups/policy | yes | true no-mock HTTP | same | `backupsPolicy_getAndRoundtrip_overRealHttp` (`:1211+`) |
| PUT /api/v1/admin/backups/policy | yes | true no-mock HTTP | same | `backupsPolicy_getAndRoundtrip_overRealHttp` (`:1211+`) |
| POST /api/v1/admin/backups/recovery-drill | yes | true no-mock HTTP | same | `backupsRecoveryDrill_withNoBackup_returns409or202` (`:1269+`) |
| GET /api/v1/admin/backups/recovery-drills | yes | true no-mock HTTP | same | `backupsRecoveryDrills_adminList_returns200` (`:1256+`) |
| GET /api/v1/admin/recycle-bin/policy | yes | true no-mock HTTP | same | `recycleBinPolicy_adminJwt_returnsRetentionDays` (`:1307+`) |
| GET /api/v1/admin/recycle-bin | yes | true no-mock HTTP | same | `recycleBinList_adminJwt_returnsPage` (`:1320+`) |
| POST /api/v1/admin/recycle-bin/:type/:id/restore | yes | true no-mock HTTP | same | `recycleBinRestore_unknownId_returns404` (`:1333+`) |
| DELETE /api/v1/admin/recycle-bin/:type/:id | yes | true no-mock HTTP | same | `recycleBinHardDelete_unknownType_returns400` (`:1357+`) |
| GET /api/v1/admin/notification-templates | yes | true no-mock HTTP | same | `notificationTemplatesList_adminJwt_returnsPage` (`:1383+`) |
| PUT /api/v1/admin/notification-templates/:key | yes | true no-mock HTTP | same | `notificationTemplatesUpdate_unknownKey_returns404` (`:1396+`) |
| GET /api/v1/notifications | yes | true no-mock HTTP | same | `notifications_list_withStudentJwt_returnsPageShape` (`:798+`) |
| POST /api/v1/notifications/:id/read | yes | true no-mock HTTP | same | `notificationsReadUnknownId_studentJwt_returns404` (`:1436+`) |
| POST /api/v1/notifications/read-all | yes | true no-mock HTTP | same | `notifications_markAllRead_returns204` (`:832+`) |
| GET /api/v1/notifications/unread-count | yes | true no-mock HTTP | same | `notifications_unreadCount_returnsUnreadCountField` (`:815+`) |
| POST /api/v1/reports | yes | true no-mock HTTP | same | `reportsLifecycle_createGetCancelList_overRealHttp` (`:585+`) |
| GET /api/v1/reports/:id | yes | true no-mock HTTP | same | `reportsLifecycle_createGetCancelList_overRealHttp` (`:585+`) |
| GET /api/v1/reports/:id/download | yes | true no-mock HTTP | same | `reportsLifecycle_createGetCancelList_overRealHttp` (`:585+`) |
| GET /api/v1/reports | yes | true no-mock HTTP | same | `reportsLifecycle_createGetCancelList_overRealHttp` (`:585+`) |
| POST /api/v1/reports/:id/cancel | yes | true no-mock HTTP | same | `reportsLifecycle_createGetCancelList_overRealHttp` (`:585+`) |
| GET /api/v1/reports/schedules | yes | true no-mock HTTP | same | `reportsScheduleLifecycle_createUpdateListDelete_overRealHttp` (`:638+`) |
| POST /api/v1/reports/schedules | yes | true no-mock HTTP | same | `reportsScheduleLifecycle_createUpdateListDelete_overRealHttp` (`:638+`) |
| PUT /api/v1/reports/schedules/:id | yes | true no-mock HTTP | same | `reportsScheduleLifecycle_createUpdateListDelete_overRealHttp` (`:638+`) |
| DELETE /api/v1/reports/schedules/:id | yes | true no-mock HTTP | same | `reportsScheduleLifecycle_createUpdateListDelete_overRealHttp` (`:638+`) |
| GET /api/v1/courses | yes | true no-mock HTTP | same | `coursesList_withStudentJwt_returnsPage` (`:848+`) |
| POST /api/v1/courses | yes | true no-mock HTTP | same | `courseAuthoring_endToEnd_createUpdateDelete` (`:1624+`) |
| PUT /api/v1/courses/:id | yes | true no-mock HTTP | same | `courseAuthoring_endToEnd_createUpdateDelete` (`:1624+`) |
| DELETE /api/v1/courses/:id | yes | true no-mock HTTP | same | `courseAuthoring_endToEnd_createUpdateDelete` (`:1624+`) |
| GET /api/v1/courses/:id/cohorts | yes | true no-mock HTTP | same | `coursesCohorts_adminJwt_returns200` (`:1707+`) |
| GET /api/v1/courses/:id/assessment-items | yes | true no-mock HTTP | same | `coursesAssessmentItems_publicCourse_returnsPage` (`:868+`) |
| GET /api/v1/courses/:courseId/knowledge-points | yes | true no-mock HTTP | same | `coursesKnowledgePoints_publicCourse_returnsArray` (`:899+`) |
| POST /api/v1/courses/:courseId/knowledge-points | yes | true no-mock HTTP | same | `courseAuthoring_endToEnd_createUpdateDelete` (`:1624+`) |
| GET /api/v1/courses/:courseId/activities | yes | true no-mock HTTP | same | `coursesActivities_publicCourse_returnsArray` (`:884+`) |
| POST /api/v1/courses/:courseId/activities | yes | true no-mock HTTP | same | `courseAuthoring_endToEnd_createUpdateDelete` (`:1624+`) |
| POST /api/v1/assessment-items | yes | true no-mock HTTP | same | `courseAuthoring_endToEnd_createUpdateDelete` (`:1624+`) |
| PUT /api/v1/assessment-items/:id | yes | true no-mock HTTP | same | `courseAuthoring_endToEnd_createUpdateDelete` (`:1624+`) |
| GET /api/v1/analytics/mastery-trends | yes | true no-mock HTTP | same | `analytics_masteryTrends_withMentorJwt_reachesRouteAndIsAuthorized` (`:695+`) |
| GET /api/v1/analytics/wrong-answers | yes | true no-mock HTTP | same | `analyticsWrongAnswers_withMentorJwt_returnsItemsEnvelope` (`:1772+`) |
| GET /api/v1/analytics/weak-knowledge-points | yes | true no-mock HTTP | same | `analytics_weakKnowledgePoints_withFacultyJwt_reachesRouteAndIsAuthorized` (`:720+`) |
| GET /api/v1/analytics/item-stats | yes | true no-mock HTTP | same | `analytics_itemStats_withMentorJwt_reachesRouteAndIsAuthorized` (`:762+`) |
| POST /api/v1/sessions | yes | true no-mock HTTP | same | `sessionsCreate_withStudentJwt_returnsCreatedDto` (`:376+`) |
| PATCH /api/v1/sessions/:id | yes | true no-mock HTTP | same | `sessionsLifecycle_createPausePatchCompleteGet_assertsEachTransition` (`:404+`) |
| POST /api/v1/sessions/:id/pause | yes | true no-mock HTTP | same | `sessionsLifecycle_createPausePatchCompleteGet_assertsEachTransition` (`:404+`) |
| POST /api/v1/sessions/:id/continue | yes | true no-mock HTTP | same | `sessionsLifecycle_createPausePatchCompleteGet_assertsEachTransition` (`:404+`) |
| POST /api/v1/sessions/:id/complete | yes | true no-mock HTTP | same | `sessionsLifecycle_createPausePatchCompleteGet_assertsEachTransition` (`:404+`) |
| POST /api/v1/sessions/:id/sets | yes | true no-mock HTTP | same | `sessionsSetsLifecycle_createPatchOverRealHttp` (`:1461+`) |
| PATCH /api/v1/sessions/:id/sets/:setId | yes | true no-mock HTTP | same | `sessionsSetsLifecycle_createPatchOverRealHttp` (`:1461+`) |
| GET /api/v1/sessions/:id | yes | true no-mock HTTP | same | `sessionsLifecycle_createPausePatchCompleteGet_assertsEachTransition` (`:404+`) |
| GET /api/v1/sessions | yes | true no-mock HTTP | same | `sessionsList_asStudent_scopedToOwnSessions` (`:514+`) |
| POST /api/v1/sessions/sync | yes | true no-mock HTTP | same | `sessionsSync_asStudent_returnsAppliedAndConflictsEnvelope` (`:538+`) |
| POST /api/v1/sessions/attempt-drafts | yes | true no-mock HTTP | same | `sessionsAttemptDrafts_fullCrudOverRealHttp` (`:1510+`) |
| GET /api/v1/sessions/:sessionId/attempt-drafts | yes | true no-mock HTTP | same | `sessionsAttemptDrafts_fullCrudOverRealHttp` (`:1510+`) |
| DELETE /api/v1/sessions/:sessionId/attempt-drafts | yes | true no-mock HTTP | same | `sessionsAttemptDrafts_fullCrudOverRealHttp` (`:1510+`) |
| POST /api/v1/sessions/:sessionId/submit-attempts | yes | true no-mock HTTP | same | `sessionsAttemptDrafts_fullCrudOverRealHttp` (`:1510+`) |

Additional HTTP-with-mocked-transport evidence (MockMvc): `server/src/test/java/com/meridian/AuthApiTest.java:24-33`, `:52-56`; `server/src/test/java/com/meridian/NoMockAuthCoverageApiTest.java:24-27`, `:42-45`.

## API Test Classification
1. True No-Mock HTTP
- `server/src/test/java/com/meridian/TrueNoMockHttpApiTest.java`.
- Evidence: real TCP port + `TestRestTemplate`, no `@AutoConfigureMockMvc` (`TrueNoMockHttpApiTest.java:50-67`).

2. HTTP with Mocking (transport-layer)
- All `*ApiTest.java` classes using `@AutoConfigureMockMvc` + `MockMvc` (for example `AuthApiTest.java:24-33`, `NoMockAuthCoverageApiTest.java:24-37`).
- Includes archival mirror under `api_tests/src/test/java/com/meridian/*.java` (same pattern).

3. Non-HTTP (unit/integration without HTTP)
- Service/controller/repository/filter unit tests under `server/src/test/java/com/meridian/**` excluding API classes.
- Example mock-heavy unit tests: `server/src/test/java/com/meridian/auth/AuthServiceTest.java:55-76`.

## Mock Detection
- API tests (`*ApiTest.java`) show no `@MockBean`, `@Mock`, `Mockito.mock`, `when(...)` hits in static grep.
- However, MockMvc suites still use mocked servlet transport and are classified as HTTP-with-mocking per strict rule.
- Explicit mocking in non-HTTP unit tests:
  - `AuthServiceTest`: mocked repositories/services (`server/src/test/java/com/meridian/auth/AuthServiceTest.java:55-65`).
  - Similar Mockito patterns across service tests (`reports/runner`, `analytics`, `approvals`, `users`, `security`, etc.).

## Coverage Summary
- Total endpoints: **76**
- Endpoints with HTTP tests (any HTTP style): **76**
- Endpoints with TRUE no-mock HTTP tests: **76**
- HTTP coverage: **100.0%**
- True API coverage: **100.0%**

## Unit Test Summary
### Backend Unit Tests
- Backend unit test files exist broadly across controllers/services/repositories/security filters.
- Covered modules (examples):
  - Controllers: `AllowedIpRangeControllerUnitTest`, `SessionControllerUnitTest`.
  - Services/runners: `AuthServiceTest`, `ApprovalServiceTest`, `AnalyticsServiceTest`, `NotificationServiceTest`, `BackupRunnerTest`, `ReportRunnerTest`.
  - Repositories: `TrainingSessionRepositoryTest`.
  - Auth/guards/middleware: `JwtAuthenticationFilterTest`, `RateLimitFilterTest`, `IdempotencyInterceptorTest`, `RequestIdFilterTest`.
- Important backend modules not unit-tested directly (API-tested instead): several controllers such as `ReportController`, `BackupController`, `NotificationController`, `AnalyticsController`, `RecycleBinController`.

### Frontend Unit Tests (STRICT)
- Frontend test files: **present** under `unit_tests/web/*.spec.ts` (48 files detected).
- Framework/tooling evidence:
  - Jasmine/Karma config: `web/karma.conf.js:4-10`.
  - TS spec config: `web/tsconfig.spec.json:5-9`.
  - Angular TestBed usage in specs: `unit_tests/web/login.component.spec.ts:20-33`.
- Real frontend modules imported/rendered:
  - `LoginComponent` from `web/src/app/auth/pages/login.component` (`login.component.spec.ts:5,23`).
  - `ApiService` from `web/src/app/core/http/api.service` (`api.service.spec.ts:3`).
- Execution wiring evidence:
  - `run_tests.sh` copies spec mirror files into `web/src/**` before running `ng test` in Docker (`run_tests.sh:375-477`).

**Frontend unit tests: PRESENT**

Important frontend modules not directly evidenced as unit-tested:
- App bootstrap/entry-level wiring (for example `web/src/main.ts`) not directly referenced by discovered unit spec files.
- End-to-end route composition is tested primarily via Playwright E2E, not deep unit specs.

### Cross-Layer Observation
- Backend API testing is extensive and includes true no-mock HTTP suite.
- Frontend has substantial unit tests plus E2E suite (`e2e_tests/tests/*.spec.ts`, example `01-register-approve-login.spec.ts:12-52`).
- Balance: generally good; not backend-only.

## Tests Check
### API Observability Check
- Strong in major suites: explicit endpoint paths, explicit request payloads/headers, and response body assertions.
- Examples:
  - True no-mock auth login asserts payload fields (`TrueNoMockHttpApiTest.java:113-126`).
  - MockMvc auth tests assert JSON response fields (`AuthApiTest.java:52-56`, `:73-74`).
- Weakness:
  - Some tests allow broad status ranges (`200/404`, `409/202`, etc.), which weakens precision for behavior regression detection.

### Test Quality & Sufficiency
- Success/failure paths: covered across auth, sessions, reports, admin boundaries.
- Validation/auth/permission checks: heavily present.
- Edge-case depth: present but uneven; some tests intentionally tolerate multiple outcomes.
- Assertion depth: mostly meaningful (status + body), not only pass/fail.

### `run_tests.sh` Check
- Docker-based backend/web test execution: yes (`run_tests.sh:473-477`).
- Frontend unit tests are copied from mirror then executed in Docker (`run_tests.sh:375-477`).
- E2E path includes in-container `npm ci` fallback (`run_tests.sh:557-560`): not host-local install, but still runtime dependency installation inside test container branch.

### End-to-End Expectation (fullstack)
- Real FE↔BE E2E tests exist (`e2e_tests/tests/*.spec.ts`, example includes UI + API calls: `01-register-approve-login.spec.ts:12-52`).
- This satisfies fullstack E2E presence expectation.

## Test Coverage Score (0–100)
**92/100**

## Score Rationale
- + Full endpoint inventory coverage with HTTP tests.
- + True no-mock HTTP suite covers all endpoint families through real network path.
- + Large backend and frontend unit test surface.
- - Precision loss in some assertions due accepted status ranges.
- - Frontend unit tests are mirror-copied into source before run (extra indirection risk).
- - E2E helper has runtime `npm ci` fallback in container path.

## Key Gaps
- Some test cases intentionally accept multiple statuses (reduces strict regression sensitivity).
- Frontend test location is indirect (`unit_tests/web` mirror + copy step), which can drift from app changes if the copy logic breaks.
- Repository-level backend unit coverage appears narrow relative to service/controller coverage.

## Confidence & Assumptions
- Confidence: **medium-high**.
- Assumptions:
  - `server/src/test/java` is authoritative for backend tests; `api_tests` mirrors are duplicates (`README.md:177`, `:237`).
  - Endpoint extraction limited to Spring mapping annotations in controller classes.
  - No runtime behavior was verified; all conclusions are static.

**Test Coverage Verdict:** PASS WITH RISKS

---

# README Audit

## Hard Gate Evaluation
- README location: present at `repo/README.md`.
- Formatting/readability: passes (clear sections, tables, commands).
- Startup instruction (`docker-compose up` required for fullstack): passes (`README.md:38`).
- Access method (URL + port): passes (`README.md:49-56`).
- Verification method: passes (`README.md:58-80`).
- Environment strictness (no local runtime installs): passes in README language (`README.md:24-27`, `:167`).
- Demo credentials for auth project: passes (`README.md:112-122`) with username/password/role table.

## Engineering Quality
- Tech stack clarity: strong (`README.md:9-18`).
- Architecture/deployment flow: documented (`README.md:186-191`).
- Test instructions: detailed (`README.md:124-167`).
- Security/roles: credentials and roles documented; auth workflows implied.
- Workflow guidance/troubleshooting: present (`README.md:82-110`).

## High Priority Issues
- None.

## Medium Priority Issues
- Minor consistency risk: README claims Playwright image has runtime dependencies preinstalled (`README.md:150`), while `run_tests.sh` retains `npm ci` fallback for E2E (`run_tests.sh:557-560`). This is not a hard-gate failure but is documentation-to-script mismatch risk.

## Low Priority Issues
- Dual command style (`docker-compose` and `docker compose`) may be mildly redundant but acceptable.
- README could explicitly call out that frontend unit specs are stored in `unit_tests/web` then copied before run, to match script behavior.

## Hard Gate Failures
- None.

## README Verdict (PASS / PARTIAL PASS / FAIL)
**PASS**

**README Verdict:** PASS
