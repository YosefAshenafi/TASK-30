# Meridian Training Analytics Management System

Project Type: Fullstack Web Application

## Architecture & Tech Stack

- **Frontend**: Angular 17 + Angular Material (TypeScript), served by Nginx on port 3000 (single access point)
- **Backend**: Spring Boot 3.2 (Java 17) REST API, reverse-proxied by Nginx under `http://localhost:3000/api` and `http://localhost:3000/actuator`
- **Database**: PostgreSQL 15 (Flyway migrations, 22 tables)
- **Containerization**: Docker + Docker Compose (all services containerized)
- **Offline**: IndexedDB (Dexie.js) + background sync with idempotency keys

## Project Structure

```text
repo/
├── backend/           Spring Boot application (Java 17)
│   ├── src/main/java/com/meridian/
│   │   ├── controller/    REST controllers (auth, sessions, analytics, reports, governance, backup)
│   │   ├── service/       Business logic
│   │   ├── entity/        JPA entities
│   │   ├── repository/    Spring Data repositories
│   │   ├── security/      JWT, encryption, filters
│   │   └── scheduler/     Quartz jobs
│   ├── src/main/resources/
│   │   └── db/migration/  Flyway SQL migrations (V1-V2)
│   ├── src/test/          API tests (Testcontainers + MockMvc)
│   ├── Dockerfile
│   └── entrypoint.sh
├── frontend/          Angular 17 application
│   ├── src/app/       Components, services, guards
│   ├── Dockerfile
│   └── nginx.conf
├── tests/
│   └── e2e/           Playwright end-to-end tests
├── docker-compose.yml
├── run_tests.sh
└── README.md
```

## Prerequisites

- Docker Desktop 24+ with Compose v2 (`docker compose version`)

## Running the Application

1. `cd repo`
2. `docker-compose up`
3. **Frontend (access point)**: http://localhost:3000
4. **Backend API** (via Nginx proxy): http://localhost:3000/api
5. **Health check**: http://localhost:3000/actuator/health
6. **Stop**: `docker-compose down -v`

> The database schema and seed data (roles, organizations, and the demo users below) are applied automatically by Flyway migrations on backend startup — no separate seed command is required. Migrations are idempotent, so repeated `docker compose up` runs are safe.

> To rebuild images after code changes: `docker-compose up --build` (or `docker compose up --build` on Compose v2).

## Testing

```bash
cd repo
chmod +x run_tests.sh
./run_tests.sh
```

**Verification** (after `docker-compose up --build`):

**API health check:**
```bash
curl http://localhost:3000/actuator/health
# Expected: {"status":"UP"}

curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@12345678"}'
# Expected: 200 JSON body containing "accessToken" and nested "user" object with username "admin"
```

**Web UI verification flow:**

1. Open http://localhost:3000 in a browser.
   - **Expected:** Redirected to `/login`; page shows "Meridian Training Analytics" title and a sign-in form with Username and Password fields.

2. Enter username `admin` / password `Admin@12345678` and click **Sign In**.
   - **Expected:** Redirected to `/dashboard`; left-hand navigation shows Dashboard, User Management, Backup & DR, and Governance links (admin-only items visible).

3. Click **Analytics** in the nav (or navigate to http://localhost:3000/analytics).
   - **Expected:** Analytics Dashboard page loads with four tabs: Mastery Trends, Wrong Answers, Knowledge Gaps, Item Difficulty. Spinner appears briefly, then data or empty-state message renders.

4. Navigate to http://localhost:3000/reports.
   - **Expected:** Reports Center page loads with tabs for Enrollments, Seat Utilization, Refunds, Inventory, Certifications. A notification bell icon is visible in the top-right of the page header.

5. Navigate to http://localhost:3000/admin/users.
   - **Expected:** User Management page loads with "Pending Approvals" and "All Users" tabs. The All Users tab shows a paginated table of seeded users (admin, faculty1, corp1, student1).

6. Click the **Logout** button in the nav.
   - **Expected:** Redirected back to `/login`.

7. Log in as `student1` / `Student@12345678`.
   - **Expected:** Redirected to `/dashboard`; navigation shows only Dashboard and My Sessions (no admin or mentor links).

8. Navigate to http://localhost:3000/sessions.
   - **Expected:** My Sessions page loads showing a table or empty state. A "New Session" button is visible.

## Seeded Credentials

| Role | Username | Password |
|---|---|---|
| Administrator | admin | Admin@12345678 |
| Faculty Mentor | faculty1 | Faculty@12345678 |
| Corporate Mentor | corp1 | Corp@12345678 |
| Student | student1 | Student@12345678 |

> All passwords satisfy the policy: >= 12 chars, >= 1 number, >= 1 symbol.

## Key Features

### Authentication & Authorization
- JWT-based authentication with refresh tokens (HttpOnly cookies)
- Role-based access control: STUDENT, FACULTY_MENTOR, CORPORATE_MENTOR, ADMINISTRATOR
- New user registration requires administrator approval

### Session Capture (Student)
- Real-time elapsed timer (HH:MM:SS)
- Configurable rest timer (15-300 seconds) with countdown
- Activity checklist with per-item completion tracking
- Offline mode: changes saved to IndexedDB, background sync on reconnect
- Idempotency keys prevent duplicate session creation

### Analytics (Mentor/Admin)
- Mastery trends with CSS bar chart visualization
- Wrong answer analysis by assessment item
- Knowledge gap identification by topic area
- Item difficulty and discrimination indices
- Date range, location, instructor, and course version filters

### Reports (Mentor/Admin)
- Five report types: Enrollments, Seat Utilization, Refunds, Inventory, Certifications
- CSV and PDF export via POST /api/reports/export
- Scheduled report delivery via cron configuration dialog
- Real-time notifications via Server-Sent Events (SSE)

### Admin Features
- User approval workflow (pending registrations)
- Role assignment for all users
- Backup management (FULL/INCREMENTAL) with history
- Recycle bin with restore and permanent delete
- Data governance: field-level permission grants with classification labels
- Recovery drill recording and audit trail

## Disaster Recovery

### Failover Procedure

1. **Detect failure**: Monitor `/actuator/health` endpoint. A response other than `{"status":"UP"}` signals a degraded or down state.
2. **Stop the primary instance**: `docker-compose down` (or stop the affected service).
3. **Restore from latest backup**: Retrieve the most recent backup file recorded in `GET /api/admin/backup/history`. Copy the backup file to the PostgreSQL container and restore:
   ```bash
   docker exec -i <postgres-container> psql -U postgres meridian < /path/to/backup.sql
   ```
4. **Restart services**: `docker-compose up` (or `docker-compose up --build` if images need rebuilding).
5. **Verify recovery**: Run the health check and confirm seeded users can log in:
   ```bash
   curl http://localhost:3000/actuator/health
   curl -X POST http://localhost:3000/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"Admin@12345678"}'
   ```
6. **Record the drill**: Log the recovery event via `POST /api/admin/recovery-drills` (see below).

### Quarterly Recovery Drill Procedure

Perform a DR drill at least once per quarter to validate backup integrity and team readiness.

**Steps:**
1. Schedule a maintenance window.
2. Trigger a fresh backup: `POST /api/admin/backup/trigger` with `{"type":"FULL"}`.
3. Spin up an isolated environment (separate Docker network or VM) and restore the backup following the Failover Procedure above.
4. Execute the Web UI Verification Flow (steps 1–8 in the Testing section) against the restored environment.
5. Record the drill outcome via the API:
   ```bash
   curl -X POST http://localhost:3000/api/admin/recovery-drills \
     -H "Authorization: Bearer <admin-token>" \
     -H "Content-Type: application/json" \
     -d '{
       "drillDate": "2026-05-21",
       "stepsCompleted": 8,
       "totalSteps": 8,
       "outcome": "PASS",
       "notes": "Q2 2026 quarterly DR drill. All verification steps passed."
     }'
   ```
   - `outcome` must be one of: `PASS`, `FAIL`, `PARTIAL`.
   - `stepsCompleted` / `totalSteps` reflect which steps of the recovery checklist were completed.
   - `notes` should capture any deviations, timing observations, or follow-up action items.
6. Review the drill history: `GET /api/admin/recovery-drills` (admin token required).
7. Address any `FAIL` or `PARTIAL` outcomes before the next quarter.
