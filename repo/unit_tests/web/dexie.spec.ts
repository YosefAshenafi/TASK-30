import { db, MeridianDatabase } from '../../web/src/app/core/db/dexie';

describe('MeridianDatabase (dexie)', () => {
  beforeEach(async () => {
    // Fresh database for each test — isolation
    await db.sessions.clear();
    await db.sessionSets.clear();
    await db.attemptDrafts.clear();
    await db.outbox.clear();
    await db.profileCache.clear();
    await db.templatesCache.clear();
    await db.syncState.clear();
  });

  it('is an instance of MeridianDatabase with the declared tables', () => {
    expect(db).toBeInstanceOf(MeridianDatabase);
    expect(db.sessions).toBeDefined();
    expect(db.sessionSets).toBeDefined();
    expect(db.attemptDrafts).toBeDefined();
    expect(db.outbox).toBeDefined();
    expect(db.profileCache).toBeDefined();
    expect(db.templatesCache).toBeDefined();
    expect(db.syncState).toBeDefined();
  });

  it('sessions table stores and retrieves a record by primary key', async () => {
    await db.sessions.put({
      id: 's1',
      studentId: 'u1',
      courseId: 'c1',
      cohortId: null,
      startedAt: '2026-04-20T09:00:00Z',
      endedAt: null,
      restSecondsDefault: 60,
      status: 'IN_PROGRESS',
      clientUpdatedAt: '2026-04-20T09:00:00Z',
    });
    const loaded = await db.sessions.get('s1');
    expect(loaded?.id).toBe('s1');
    expect(loaded?.status).toBe('IN_PROGRESS');
  });

  it('sessions table can be queried by status index', async () => {
    await db.sessions.bulkPut([
      { id: 's1', studentId: 'u1', courseId: 'c1', cohortId: null, startedAt: '', endedAt: null,
        restSecondsDefault: 60, status: 'IN_PROGRESS', clientUpdatedAt: '' },
      { id: 's2', studentId: 'u1', courseId: 'c1', cohortId: null, startedAt: '', endedAt: null,
        restSecondsDefault: 60, status: 'COMPLETED', clientUpdatedAt: '' },
    ]);
    const active = await db.sessions.where('status').equals('IN_PROGRESS').toArray();
    expect(active.length).toBe(1);
    expect(active[0].id).toBe('s1');
  });

  it('attemptDrafts can be queried by sessionId and deleted', async () => {
    await db.attemptDrafts.bulkPut([
      { id: 's1:i1', sessionId: 's1', itemId: 'i1', chosenAnswer: 'A', clientUpdatedAt: '' },
      { id: 's1:i2', sessionId: 's1', itemId: 'i2', chosenAnswer: 'B', clientUpdatedAt: '' },
      { id: 's2:i1', sessionId: 's2', itemId: 'i1', chosenAnswer: 'C', clientUpdatedAt: '' },
    ]);
    const s1 = await db.attemptDrafts.where('sessionId').equals('s1').toArray();
    expect(s1.length).toBe(2);

    await db.attemptDrafts.where('sessionId').equals('s1').delete();
    const remaining = await db.attemptDrafts.toArray();
    expect(remaining.length).toBe(1);
    expect(remaining[0].sessionId).toBe('s2');
  });

  it('outbox uses auto-incrementing id and preserves FIFO order', async () => {
    const now = new Date();
    const k1 = await db.outbox.add({
      method: 'POST', url: '/a', body: {}, idempotencyKey: 'k1',
      clientUpdatedAt: now.toISOString(), attempts: 0, nextRetryAt: 0, createdAt: now.toISOString(),
    });
    const k2 = await db.outbox.add({
      method: 'POST', url: '/b', body: {}, idempotencyKey: 'k2',
      clientUpdatedAt: now.toISOString(), attempts: 0, nextRetryAt: 0, createdAt: new Date(now.getTime() + 1).toISOString(),
    });
    expect(k2).toBeGreaterThan(k1);

    const byOrder = await db.outbox.orderBy('createdAt').toArray();
    expect(byOrder.map(o => o.idempotencyKey)).toEqual(['k1', 'k2']);
  });

  it('syncState supports the "state" singleton row pattern', async () => {
    await db.syncState.put({ singleton: 'state', lastSuccessfulSyncAt: null, pendingCount: 0 });
    let loaded = await db.syncState.get('state');
    expect(loaded?.pendingCount).toBe(0);

    await db.syncState.put({ singleton: 'state', lastSuccessfulSyncAt: '2026-04-20T09:00:00Z', pendingCount: 3 });
    loaded = await db.syncState.get('state');
    expect(loaded?.pendingCount).toBe(3);
    expect(loaded?.lastSuccessfulSyncAt).toBe('2026-04-20T09:00:00Z');
  });

  it('profileCache stores a user profile keyed by userId', async () => {
    await db.profileCache.put({
      userId: 'user-1',
      profile: { username: 'alice', role: 'STUDENT' },
      cachedAt: '2026-04-20T09:00:00Z',
    });
    const loaded = await db.profileCache.get('user-1');
    expect(loaded?.userId).toBe('user-1');
    expect((loaded?.profile as any).username).toBe('alice');
  });
});
