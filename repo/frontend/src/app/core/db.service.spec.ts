import { DbService, MeridianDb, DraftAssessment, LocalSession } from './db.service';
import { TestBed } from '@angular/core/testing';

describe('DbService', () => {
  let service: DbService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [DbService] });
    service = TestBed.inject(DbService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('db property is a MeridianDb instance', () => {
    expect(service.db).toBeInstanceOf(MeridianDb);
  });

  it('db has a sessions table', () => {
    expect(service.db.sessions).toBeDefined();
  });

  it('db has a draftAssessments table', () => {
    expect(service.db.draftAssessments).toBeDefined();
  });
});

describe('DraftAssessment interface shape', () => {
  it('DraftAssessment includes all required sync fields', () => {
    const draft: DraftAssessment = {
      sessionId: 'sess-001',
      itemId: 'item-001',
      answer: 'C',
      flagged: true,
      timeSpentSecs: 45,
      idempotencyKey: 'key-001',
      syncStatus: 'PENDING',
      lastModified: new Date().toISOString(),
    };

    expect(draft.sessionId).toBe('sess-001');
    expect(draft.itemId).toBe('item-001');
    expect(draft.answer).toBe('C');
    expect(draft.flagged).toBeTrue();
    expect(draft.timeSpentSecs).toBe(45);
    expect(draft.idempotencyKey).toBe('key-001');
    expect(draft.syncStatus).toBe('PENDING');
    expect(draft.lastModified).toBeDefined();
  });

  it('DraftAssessment syncStatus accepts PENDING, SYNCED, CONFLICT', () => {
    const pending: DraftAssessment['syncStatus'] = 'PENDING';
    const synced: DraftAssessment['syncStatus'] = 'SYNCED';
    const conflict: DraftAssessment['syncStatus'] = 'CONFLICT';

    expect(pending).toBe('PENDING');
    expect(synced).toBe('SYNCED');
    expect(conflict).toBe('CONFLICT');
  });
});

describe('LocalSession interface shape', () => {
  it('LocalSession includes all required fields', () => {
    const session: LocalSession = {
      idempotencyKey: 'sess-key-001',
      courseId: 'course-uuid-001',
      status: 'IN_PROGRESS',
      restTimerSecs: 60,
      startedAt: new Date().toISOString(),
      activities: [],
      syncStatus: 'PENDING',
      updatedAt: new Date().toISOString(),
    };

    expect(session.idempotencyKey).toBe('sess-key-001');
    expect(session.syncStatus).toBe('PENDING');
    expect(session.activities).toEqual([]);
  });
});
