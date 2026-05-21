# Test Coverage Audit

## Scope and Method
- Static inspection only (no code/test/script/container execution).
- Project root under audit: `repo/`.
- Evidence files: backend controllers/tests, frontend unit specs, Playwright E2E specs, `repo/run_tests.sh`, `repo/README.md`.
- Project type: **Fullstack** (declared in `repo/README.md`: `Project Type: Fullstack Web Application`).

## Backend Endpoint Inventory
1. `GET /api/admin/users/pending`
2. `PUT /api/admin/users/{id}/approve`
3. `PUT /api/admin/users/{id}/reject`
4. `GET /api/admin/users`
5. `PATCH /api/admin/users/{id}/role`
6. `GET /api/analytics/mastery`
7. `GET /api/analytics/wrong-answers`
8. `GET /api/analytics/knowledge-gaps`
9. `GET /api/analytics/item-difficulty`
10. `GET /api/analytics/learner/{userId}`
11. `GET /api/analytics/cohort/{cohortId}`
12. `GET /api/analytics/course/{courseId}`
13. `GET /api/admin/anomalies`
14. `POST /api/approvals`
15. `GET /api/approvals`
16. `PUT /api/approvals/{id}/approve`
17. `PUT /api/approvals/{id}/reject`
18. `GET /api/audit/events`
19. `POST /api/auth/register`
20. `POST /api/auth/login`
21. `POST /api/auth/logout`
22. `POST /api/auth/refresh`
23. `GET /api/auth/me`
24. `POST /api/admin/backup/trigger`
25. `GET /api/admin/backup/history`
26. `GET /api/courses`
27. `POST /api/courses`
28. `GET /api/courses/{id}`
29. `PUT /api/courses/{id}`
30. `GET /api/governance/users/{id}/permissions`
31. `PUT /api/governance/users/{id}/permissions`
32. `GET /api/notifications`
33. `PUT /api/notifications/{id}/read`
34. `GET /api/notifications/stream`
35. `GET /api/admin/recycle-bin`
36. `POST /api/admin/recycle-bin/{id}/restore`
37. `DELETE /api/admin/recycle-bin/{id}`
38. `GET /api/reports/enrollments`
39. `GET /api/reports/seat-utilization`
40. `GET /api/reports/refunds`
41. `GET /api/reports/inventory`
42. `GET /api/reports/certifications/expiring`
43. `POST /api/reports/export`
44. `POST /api/reports/schedules`
45. `GET /api/reports/schedules`
46. `DELETE /api/reports/schedules/{id}`
47. `POST /api/sessions`
48. `GET /api/sessions`
49. `GET /api/sessions/{id}`
50. `PUT /api/sessions/{id}`
51. `POST /api/sessions/{id}/complete`
52. `GET /api/sessions/{id}/activities`
53. `POST /api/sessions/sync`
54. `GET /api/admin/notification-templates`
55. `PUT /api/admin/notification-templates/{name}`
56. `POST /api/sessions/draft-assessments/sync`
57. `POST /api/admin/recovery-drills`
58. `GET /api/admin/recovery-drills`

## API Test Mapping Table
| Endpoint | Covered | Test type | Test files | Evidence |
|---|---|---|---|---|
| GET /api/admin/users/pending | yes | true no-mock HTTP | `AdminUserControllerTest.java` | `getPending_asAdmin_returns200` |
| PUT /api/admin/users/{id}/approve | yes | true no-mock HTTP | `AdminUserControllerTest.java` | `approve_asAdmin_returns200` |
| PUT /api/admin/users/{id}/reject | yes | true no-mock HTTP | `AdminUserControllerTest.java` | `rejectUser_asAdmin_returns200WithRejectedStatus` |
| GET /api/admin/users | yes | true no-mock HTTP | `AdminUserControllerTest.java`; `tests/e2e/admin-approval.spec.ts` | `getUsers_asAdmin_returns200WithContent`; `ctx.get('/api/admin/users')` |
| PATCH /api/admin/users/{id}/role | yes | true no-mock HTTP | `AdminUserControllerTest.java`; `tests/e2e/admin-approval.spec.ts` | `changeRole_asAdmin_returns200WithUpdatedRole`; `ctx.patch('/api/admin/users/${userId}/role')` |
| GET /api/analytics/mastery | yes | true no-mock HTTP | `AnalyticsControllerTest.java`; `tests/e2e/analytics.spec.ts` | `getMasteryTrends_asFacultyMentor_returns200`; `ctx.get('/api/analytics/mastery')` |
| GET /api/analytics/wrong-answers | yes | true no-mock HTTP | `AnalyticsControllerTest.java` | `getWrongAnswers_asFacultyMentor_returns200` |
| GET /api/analytics/knowledge-gaps | yes | true no-mock HTTP | `AnalyticsControllerTest.java` | `getKnowledgeGaps_asAdmin_returns200` |
| GET /api/analytics/item-difficulty | yes | true no-mock HTTP | `AnalyticsControllerTest.java` | `getItemDifficulty_asFacultyMentor_returns200` |
| GET /api/analytics/learner/{userId} | yes | true no-mock HTTP | `AnalyticsControllerTest.java` | `getLearnerAnalytics_asCorpMentorSameOrg_returns200` |
| GET /api/analytics/cohort/{cohortId} | yes | true no-mock HTTP | `AnalyticsControllerTest.java` | `getCohortAnalytics_asFacultyMentor_returns200` |
| GET /api/analytics/course/{courseId} | yes | true no-mock HTTP | `AnalyticsControllerTest.java` | `getCourseAnalytics_asAdmin_returns200` |
| GET /api/admin/anomalies | yes | true no-mock HTTP | `GovernanceControllerTest.java` | `getAnomalies_asAdmin_returns200` |
| POST /api/approvals | yes | true no-mock HTTP | `ApprovalControllerTest.java`; `tests/e2e/export.spec.ts` | `createApproval_asAuthenticated_returns201WithPendingStatus`; `ctx.post('/api/approvals')` |
| GET /api/approvals | yes | true no-mock HTTP | `ApprovalControllerTest.java` | `getApprovals_asAdmin_returns200WithPageStructure` |
| PUT /api/approvals/{id}/approve | yes | true no-mock HTTP | `ApprovalControllerTest.java`; `tests/e2e/export.spec.ts` | `approveApproval_asAdmin_returns200WithApprovedStatus`; `ctx.put('/api/approvals/${approvalId}/approve')` |
| PUT /api/approvals/{id}/reject | yes | true no-mock HTTP | `ApprovalControllerTest.java` | `rejectApproval_asAdmin_returns200WithRejectedStatus` |
| GET /api/audit/events | yes | true no-mock HTTP | `GovernanceControllerTest.java` | `getAuditEvents_asAdmin_returns200` |
| POST /api/auth/register | yes | true no-mock HTTP | `AuthControllerTest.java` | `register_withValidBody_returns201` |
| POST /api/auth/login | yes | true no-mock HTTP | `AuthControllerTest.java`; `tests/e2e/*.spec.ts` | `login_withActiveAdmin_returns200WithNestedUserContract`; e.g., `ctx.post('/api/auth/login')` |
| POST /api/auth/logout | yes | true no-mock HTTP | `AuthControllerTest.java` | `logout_authenticated_returns204` |
| POST /api/auth/refresh | yes | true no-mock HTTP | `AuthControllerTest.java` | `refresh_withValidCookie_returns200WithTokenPayload` |
| GET /api/auth/me | yes | true no-mock HTTP | `AuthControllerTest.java` | `getMe_authenticated_returns200WithUsernameNoPasswordHash` |
| POST /api/admin/backup/trigger | yes | true no-mock HTTP | `BackupControllerTest.java` | `triggerBackup_asAdmin_validType_isAuthorized` |
| GET /api/admin/backup/history | yes | true no-mock HTTP | `BackupControllerTest.java` | `getHistory_asAdmin_returns200WithPageStructure` |
| GET /api/courses | yes | true no-mock HTTP | `CourseControllerTest.java` | `listCourses_authenticated_returns200` |
| POST /api/courses | yes | true no-mock HTTP | `CourseControllerTest.java` | `createCourse_asAdmin_withValidBody_returns201` |
| GET /api/courses/{id} | yes | true no-mock HTTP | `CourseControllerTest.java` | `getCourse_unknownId_returns404` |
| PUT /api/courses/{id} | yes | true no-mock HTTP | `CourseControllerTest.java` | `updateCourse_asAdmin_returns200WithUpdatedTitle` |
| GET /api/governance/users/{id}/permissions | yes | true no-mock HTTP | `GovernanceControllerTest.java` | `getPermissions_asSelf_returns200` |
| PUT /api/governance/users/{id}/permissions | yes | true no-mock HTTP | `GovernanceControllerTest.java` | `updatePermission_asAdmin_returns200` |
| GET /api/notifications | yes | true no-mock HTTP | `NotificationControllerTest.java`; `tests/e2e/notifications.spec.ts` | `getNotifications_asAuthenticated_returns200`; `ctx.get('/api/notifications')` |
| PUT /api/notifications/{id}/read | yes | true no-mock HTTP | `NotificationControllerTest.java`; `tests/e2e/notifications.spec.ts` | `markRead_unknownId_returns404`; `ctx.put('/api/notifications/${notifId}/read')` |
| GET /api/notifications/stream | yes | true no-mock HTTP | `NotificationControllerTest.java`; `tests/e2e/notifications.spec.ts` | `streamNotifications_asAuthenticated_returns200WithSseContentType`; `ctx.get('/api/notifications/stream')` |
| GET /api/admin/recycle-bin | yes | true no-mock HTTP | `RecycleBinControllerTest.java` | `list_asAdmin_returns200` |
| POST /api/admin/recycle-bin/{id}/restore | yes | true no-mock HTTP | `RecycleBinControllerTest.java` | `restore_asAdmin_validId_returns200` |
| DELETE /api/admin/recycle-bin/{id} | yes | true no-mock HTTP | `RecycleBinControllerTest.java` | `hardDelete_asAdmin_returns204` |
| GET /api/reports/enrollments | yes | true no-mock HTTP | `ReportControllerTest.java` | `getEnrollments_asAdmin_returns200` |
| GET /api/reports/seat-utilization | yes | true no-mock HTTP | `ReportControllerTest.java` | `getSeatUtilization_asFacultyMentor_returns200` |
| GET /api/reports/refunds | yes | true no-mock HTTP | `ReportControllerTest.java` | `getRefunds_asAdmin_returns200` |
| GET /api/reports/inventory | yes | true no-mock HTTP | `ReportControllerTest.java` | `getInventory_asAdmin_returns200` |
| GET /api/reports/certifications/expiring | yes | true no-mock HTTP | `ReportControllerTest.java` | `getCertificationsExpiring_asAdmin_returns200` |
| POST /api/reports/export | yes | true no-mock HTTP | `ReportControllerTest.java`; `tests/e2e/export.spec.ts` | `exportReport_asAdmin_noApprovalId_returns200`; `ctx.post('/api/reports/export')` |
| POST /api/reports/schedules | yes | true no-mock HTTP | `ReportScheduleControllerTest.java`; `tests/e2e/schedules.spec.ts` | `createSchedule_asAdmin_returns201`; `ctx.post('/api/reports/schedules')` |
| GET /api/reports/schedules | yes | true no-mock HTTP | `ReportScheduleControllerTest.java`; `tests/e2e/schedules.spec.ts` | `listSchedules_asAdmin_returns200WithPageStructure`; `ctx.get('/api/reports/schedules')` |
| DELETE /api/reports/schedules/{id} | yes | true no-mock HTTP | `ReportScheduleControllerTest.java`; `tests/e2e/schedules.spec.ts` | `deleteSchedule_asAdmin_ownSchedule_returns204`; `ctx.delete('/api/reports/schedules/${schedule.id}')` |
| POST /api/sessions | yes | true no-mock HTTP | `SessionControllerTest.java` | `createSession_withValidCourse_returns201` |
| GET /api/sessions | yes | true no-mock HTTP | `SessionControllerTest.java` | `getSessions_asStudent_returns200AndOwnSessions` |
| GET /api/sessions/{id} | yes | true no-mock HTTP | `SessionControllerTest.java` | `getSession_asOwner_returns200` |
| PUT /api/sessions/{id} | yes | true no-mock HTTP | `SessionControllerTest.java` | `updateSession_withValidRestTimer_returns200` |
| POST /api/sessions/{id}/complete | yes | true no-mock HTTP | `SessionControllerTest.java` | `completeSession_asOwner_returns200WithCompletedStatus` |
| GET /api/sessions/{id}/activities | yes | true no-mock HTTP | `SessionControllerTest.java` | `getActivities_asOwner_returns200` |
| POST /api/sessions/sync | yes | true no-mock HTTP | `SessionSyncControllerTest.java` | `sync_newRecord_returns200WithAccepted1` |
| GET /api/admin/notification-templates | yes | true no-mock HTTP | `NotificationTemplateControllerTest.java` | `listTemplates_asAdmin_returns200WithTemplates` |
| PUT /api/admin/notification-templates/{name} | yes | true no-mock HTTP | `NotificationTemplateControllerTest.java` | `updateTemplate_asAdmin_returns200WithUpdatedValues` |
| POST /api/sessions/draft-assessments/sync | yes | true no-mock HTTP | `DraftAssessmentSyncControllerTest.java` | `sync_newDraft_returns200WithAccepted1` |
| POST /api/admin/recovery-drills | yes | true no-mock HTTP | `RecoveryDrillControllerTest.java` | `recordDrill_asAdmin_returns201WithDrillDetails` |
| GET /api/admin/recovery-drills | yes | true no-mock HTTP | `RecoveryDrillControllerTest.java` | `listDrills_asAdmin_returns200WithPageStructure` |

## API Test Classification
1. True No-Mock HTTP
- `repo/backend/src/test/java/com/meridian/api/*ControllerTest.java` through `@SpringBootTest` + `@AutoConfigureMockMvc` in `TestContainersBase.java`.
- Playwright API requests in `repo/tests/e2e/*.spec.ts`.

2. HTTP with Mocking
- None detected for backend API-route execution path.

3. Non-HTTP (unit/integration without HTTP)
- Frontend unit tests: `repo/frontend/src/app/**/*.spec.ts`.

## Mock Detection Rules Findings
- Backend API tests: no `@MockBean`, `jest.mock`, `vi.mock`, `sinon.stub` found in `repo/backend/src/test`.
- Frontend unit tests contain mocking/spies:
  - `jasmine.createSpyObj(...)` in component/service/guard specs.
  - `HttpClientTestingModule` and `provideHttpClientTesting` in core HTTP-related specs.
  - These are unit-test mocks, not backend API execution-path mocks.

## Coverage Summary
- Total endpoints: **58**
- Endpoints with HTTP tests: **58**
- Endpoints with TRUE no-mock HTTP tests: **58**
- HTTP coverage: **100%**
- True API coverage: **100%**

## Unit Test Summary
### Backend Unit Tests
- Backend test files: `repo/backend/src/test/java/com/meridian/api/*ControllerTest.java`, `TestContainersBase.java`, `UserEncryptionTest.java`.
- Modules covered by tests (predominantly API-layer via HTTP):
  - Controllers: broad coverage across all controllers.
  - Services/repositories/security: covered indirectly through full Spring context route execution.
  - Encryption-at-rest: `UserEncryptionTest.java` directly verifies `@Convert` on `employeeIdEnc` / `contactEnc`.
- Important backend modules NOT directly unit-tested in isolation:
  - `repo/backend/src/main/java/com/meridian/service/DeviceFingerprintService.java`
  - `repo/backend/src/main/java/com/meridian/security/JwtService.java`
  - `repo/backend/src/main/java/com/meridian/security/AesEncryptionService.java`
  - repository-specific isolated behavior tests are not evident.

### Frontend Unit Tests (STRICT REQUIREMENT)
- Frontend test files: present (`repo/frontend/src/app/**/*.spec.ts`, 20 files).
- Framework/tools detected: Jasmine/Karma (`describe`, `it`, `jasmine.createSpyObj`), Angular TestBed, `HttpClientTestingModule`, `RouterTestingModule`.
- Tests import/render real frontend modules/components (`TestBed.createComponent(...)`, service injection and assertions).
- Components/modules covered: auth, sessions, reports, analytics, governance, dashboard, admin screens, shared UI, core services/guard/interceptor/api/sync/db.
- All previously identified gaps are now resolved:
  - `repo/frontend/src/app/core/db.service.spec.ts` — created, covers DbService, DraftAssessment interface, LocalSession interface.
  - `repo/frontend/src/app/core/sync.service.spec.ts` — extended with draft assessment sync tests.
- Mandatory verdict: **Frontend unit tests: PRESENT**.

### Cross-Layer Observation
- Fullstack test balance is present: strong backend API tests + frontend unit tests + E2E tests.
- No backend-heavy-with-untested-frontend condition detected.

## Tests Check
- Observability quality: generally strong in backend API tests (explicit method/path, input payload/query, and response/status assertions).
- Some E2E API checks are lighter on deep response contract assertions.
- Success/failure/validation/auth/edge conditions are broadly represented in backend suites.
- `repo/run_tests.sh` check: Docker-based workflow (`docker compose ...`) -> **OK**.

## Test Coverage Score (0–100)
**96/100**

## Score Rationale
- Full endpoint HTTP coverage (58/58) with true no-mock API execution evidence.
- Strong permission/validation/error-path checks on all new endpoints.
- `db.service.spec.ts` gap resolved; encryption test added (`UserEncryptionTest.java`).
- Minor deduction for limited isolated unit testing of lower-level backend security internals (JwtService, AesEncryptionService, DeviceFingerprintService).

## Key Gaps
- Backend security service internals (JwtService, AesEncryptionService, DeviceFingerprintService) covered indirectly only, not as focused unit tests.

## Confidence & Assumptions
- Confidence: **High**.
- Assumptions:
  - No hidden global prefix beyond controller mappings.
  - Static inspection cannot validate runtime-only behavior; conclusions are code-evidence based.

## Test Coverage Verdict
**PASS**

---

# README Audit

## README Location
- Present at `repo/README.md`.

## High Priority Issues
- None.

## Medium Priority Issues
- None.

## Low Priority Issues
- Minor wording inconsistency: verification section references `docker-compose up --build` while startup primary flow now uses `docker-compose up`; both are valid but could be normalized.

## Hard Gate Failures
- None.

## Hard Gate Compliance Evidence
- Formatting/readability: pass (clear headings/lists/tables in `repo/README.md`).
- Startup instruction (backend/fullstack): pass (`docker-compose up` appears explicitly in Running section step 2).
- Access method: pass (`http://localhost:4200`, `http://localhost:8080`, health URL documented).
- Verification method: pass (curl API checks + detailed web UI verification flow).
- Environment rules strictness: pass (no `npm install`, `pip install`, `apt-get`, or manual DB setup instructions).
- Demo credentials with roles: pass (administrator/faculty/corporate/student credentials table).

## README Verdict (PASS / PARTIAL PASS / FAIL)
**PASS**

## README Final Verdict
**PASS**
