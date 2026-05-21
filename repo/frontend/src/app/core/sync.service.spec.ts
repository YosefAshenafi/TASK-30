import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { SyncService, SyncResult } from './sync.service';
import { db, DraftAssessment, LocalSession } from './db.service';
import { environment } from '../../environments/environment';

const SYNC_URL = `${environment.apiUrl}/api/sessions/sync`;

describe('SyncService', () => {
  let service: SyncService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [SyncService],
    });
    service = TestBed.inject(SyncService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    service.ngOnDestroy();
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('SyncResult interface has accepted, rejected, duplicates, rejectedKeys fields', () => {
    const result: SyncResult = {
      accepted: 3,
      rejected: 1,
      duplicates: 0,
      rejectedKeys: ['key-abc'],
    };

    expect(result.accepted).toBe(3);
    expect(result.rejected).toBe(1);
    expect(result.duplicates).toBe(0);
    expect(result.rejectedKeys).toEqual(['key-abc']);
  });

  it('sync request sends raw array (not wrapped object) with required fields', fakeAsync(async () => {
    const pendingSession: LocalSession = {
      idempotencyKey: 'test-key-001',
      courseId: 'course-uuid-001',
      status: 'IN_PROGRESS',
      syncStatus: 'PENDING',
      restTimerSecs: 60,
      startedAt: new Date().toISOString(),
      completedAt: undefined,
      activities: [],
      updatedAt: new Date().toISOString(),
    };

    await db.sessions.put(pendingSession);

    const syncResult: SyncResult = { accepted: 1, rejected: 0, duplicates: 0, rejectedKeys: [] };

    // Trigger online event to start sync
    window.dispatchEvent(new Event('online'));
    tick(100);

    const req = httpMock.expectOne(SYNC_URL);

    // Must be a raw array, not { sessions: [...] }
    expect(Array.isArray(req.request.body)).toBeTrue();
    expect(req.request.body.length).toBe(1);

    const payload = req.request.body[0];
    expect(payload['idempotencyKey']).toBe('test-key-001');
    expect(payload['courseId']).toBe('course-uuid-001');
    expect(payload['status']).toBe('IN_PROGRESS');
    expect(payload['restTimerSecs']).toBe(60);
    expect('activities' in payload).toBeTrue();
    expect('startedAt' in payload).toBeTrue();
    expect('clientUpdatedAt' in payload).toBeTrue();

    req.flush(syncResult);
    tick(100);

    // Session should be marked SYNCED when not in rejectedKeys
    const updated = await db.sessions.get('test-key-001');
    expect(updated?.syncStatus).toBe('SYNCED');

    await db.sessions.delete('test-key-001');
  }));

  it('marks session CONFLICT when idempotencyKey appears in rejectedKeys', fakeAsync(async () => {
    const pendingSession: LocalSession = {
      idempotencyKey: 'conflict-key-002',
      courseId: 'course-uuid-001',
      status: 'IN_PROGRESS',
      syncStatus: 'PENDING',
      restTimerSecs: 60,
      startedAt: new Date().toISOString(),
      completedAt: undefined,
      activities: [],
      updatedAt: new Date().toISOString(),
    };

    await db.sessions.put(pendingSession);

    const syncResult: SyncResult = {
      accepted: 0,
      rejected: 1,
      duplicates: 0,
      rejectedKeys: ['conflict-key-002'],
    };

    window.dispatchEvent(new Event('online'));
    tick(100);

    const req = httpMock.expectOne(SYNC_URL);
    req.flush(syncResult);
    tick(100);

    const updated = await db.sessions.get('conflict-key-002');
    expect(updated?.syncStatus).toBe('CONFLICT');

    await db.sessions.delete('conflict-key-002');
  }));

  it('isOffline reflects navigator.onLine', () => {
    expect(service.isOffline()).toBe(!navigator.onLine);
  });

  it('syncPendingDraftAssessments sends draft payload to correct URL', fakeAsync(async () => {
    const draft: DraftAssessment = {
      sessionId: 'session-uuid-001',
      itemId: 'item-uuid-001',
      answer: 'B',
      flagged: false,
      timeSpentSecs: 30,
      idempotencyKey: 'draft-key-' + Date.now(),
      syncStatus: 'PENDING',
      lastModified: new Date().toISOString(),
    };

    await db.draftAssessments.add(draft);

    service.syncPendingDraftAssessments();
    tick(100);

    const req = httpMock.expectOne(
      `${environment.apiUrl}/api/sessions/draft-assessments/sync`
    );

    expect(Array.isArray(req.request.body)).toBeTrue();
    expect(req.request.body.length).toBe(1);

    const payload = req.request.body[0];
    expect(payload['idempotencyKey']).toBe(draft.idempotencyKey);
    expect(payload['answer']).toBe('B');
    expect(payload['flagged']).toBeFalse();
    expect(payload['timeSpentSecs']).toBe(30);
    expect('lastModified' in payload).toBeTrue();

    const syncResult: SyncResult = { accepted: 1, rejected: 0, duplicates: 0, rejectedKeys: [] };
    req.flush(syncResult);
    tick(100);

    const remaining = await db.draftAssessments
      .where('idempotencyKey')
      .equals(draft.idempotencyKey)
      .toArray();

    if (remaining.length > 0 && remaining[0].id !== undefined) {
      await db.draftAssessments.delete(remaining[0].id);
    }
  }));

  it('syncPendingDraftAssessments skips empty queue without HTTP call', fakeAsync(async () => {
    await db.draftAssessments.clear();

    service.syncPendingDraftAssessments();
    tick(100);

    httpMock.expectNone(`${environment.apiUrl}/api/sessions/draft-assessments/sync`);
  }));
});
