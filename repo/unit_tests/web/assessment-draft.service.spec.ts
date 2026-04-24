import { TestBed } from '@angular/core/testing';
import { AssessmentDraftService } from '../../web/src/app/sessions/assessment-draft.service';
import { OutboxService } from '../../web/src/app/core/http/outbox.service';
import { db } from '../../web/src/app/core/db/dexie';

class OutboxStub {
  enqueued: any[] = [];
  enqueue = (entry: any) => { this.enqueued.push(entry); return Promise.resolve(); };
}

describe('AssessmentDraftService', () => {
  let service: AssessmentDraftService;
  let outbox: OutboxStub;

  beforeEach(async () => {
    outbox = new OutboxStub();
    TestBed.configureTestingModule({
      providers: [
        AssessmentDraftService,
        { provide: OutboxService, useValue: outbox },
      ],
    });
    service = TestBed.inject(AssessmentDraftService);
    await db.attemptDrafts.clear();
  });

  it('saveDraft persists a record keyed by sessionId:itemId and enqueues a POST', async () => {
    const record = await service.saveDraft('s1', 'i1', 'A');
    expect(record.id).toBe('s1:i1');
    expect(record.sessionId).toBe('s1');
    expect(record.itemId).toBe('i1');
    expect(record.chosenAnswer).toBe('A');
    expect(record.clientUpdatedAt).toBeTruthy();

    const stored = await db.attemptDrafts.get('s1:i1');
    expect(stored?.chosenAnswer).toBe('A');

    expect(outbox.enqueued.length).toBe(1);
    expect(outbox.enqueued[0].method).toBe('POST');
    expect(outbox.enqueued[0].url).toBe('/api/v1/sessions/attempt-drafts');
    expect(outbox.enqueued[0].body.id).toBe('s1:i1');
    expect(outbox.enqueued[0].idempotencyKey).toContain('draft-s1:i1');
  });

  it('saveDraft overwrites an existing record for the same session+item', async () => {
    await service.saveDraft('s1', 'i1', 'A');
    await service.saveDraft('s1', 'i1', 'B');
    const stored = await db.attemptDrafts.get('s1:i1');
    expect(stored?.chosenAnswer).toBe('B');
    // Both writes should enqueue a sync — the outbox itself dedupes on attempts
    expect(outbox.enqueued.length).toBe(2);
  });

  it('saveDraft allows a null chosenAnswer to represent "cleared"', async () => {
    const record = await service.saveDraft('s1', 'i1', null);
    expect(record.chosenAnswer).toBeNull();
    const stored = await db.attemptDrafts.get('s1:i1');
    expect(stored?.chosenAnswer).toBeNull();
  });

  it('getDraft returns undefined when no record exists for the session+item', async () => {
    const got = await service.getDraft('nope', 'nope');
    expect(got).toBeUndefined();
  });

  it('getDraft returns the stored record when present', async () => {
    await service.saveDraft('s1', 'i1', 'C');
    const got = await service.getDraft('s1', 'i1');
    expect(got?.chosenAnswer).toBe('C');
  });

  it('listForSession returns drafts scoped to a single sessionId', async () => {
    await service.saveDraft('s1', 'i1', 'A');
    await service.saveDraft('s1', 'i2', 'B');
    await service.saveDraft('s2', 'i1', 'C');
    const s1 = await service.listForSession('s1');
    expect(s1.length).toBe(2);
    expect(s1.map(d => d.itemId).sort()).toEqual(['i1', 'i2']);
  });

  it('removeDraft deletes a single entry by session+item', async () => {
    await service.saveDraft('s1', 'i1', 'A');
    await service.saveDraft('s1', 'i2', 'B');
    await service.removeDraft('s1', 'i1');
    const remaining = await service.listForSession('s1');
    expect(remaining.map(d => d.itemId)).toEqual(['i2']);
  });

  it('clearForSession removes every draft belonging to a session', async () => {
    await service.saveDraft('s1', 'i1', 'A');
    await service.saveDraft('s1', 'i2', 'B');
    await service.saveDraft('s2', 'i1', 'C');
    await service.clearForSession('s1');
    const s1 = await service.listForSession('s1');
    const s2 = await service.listForSession('s2');
    expect(s1.length).toBe(0);
    expect(s2.length).toBe(1);
  });
});
