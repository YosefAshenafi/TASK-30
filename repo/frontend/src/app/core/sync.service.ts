import { Injectable, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { fromEvent, merge, Observable, Subscription } from 'rxjs';
import { map, filter, switchMap } from 'rxjs/operators';
import { db, DraftAssessment, LocalSession } from './db.service';
import { environment } from '../../environments/environment';

const BATCH_SIZE = 500;

export interface SyncResult {
  accepted: number;
  rejected: number;
  duplicates: number;
  rejectedKeys: string[];
}

@Injectable({ providedIn: 'root' })
export class SyncService implements OnDestroy {
  readonly offlineMode$: Observable<boolean>;
  private subscription: Subscription;

  constructor(private http: HttpClient) {
    this.offlineMode$ = merge(
      fromEvent(window, 'online').pipe(map(() => false)),
      fromEvent(window, 'offline').pipe(map(() => true))
    );

    this.subscription = fromEvent(window, 'online')
      .pipe(
        filter(() => true),
        switchMap(() => this.syncAll())
      )
      .subscribe();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }

  isOffline(): boolean {
    return !navigator.onLine;
  }

  private async syncAll(): Promise<void> {
    await this.syncPendingSessions();
    await this.syncPendingDraftAssessments();
  }

  async syncPendingDraftAssessments(): Promise<void> {
    const pending: DraftAssessment[] = await db.draftAssessments
      .where('syncStatus')
      .equals('PENDING')
      .toArray();

    if (pending.length === 0) {
      return;
    }

    for (let i = 0; i < pending.length; i += BATCH_SIZE) {
      const batch = pending.slice(i, i + BATCH_SIZE);
      await this.syncDraftBatch(batch);
    }
  }

  private async syncDraftBatch(drafts: DraftAssessment[]): Promise<void> {
    return new Promise<void>((resolve) => {
      const payload = drafts.map((d) => ({
        idempotencyKey: d.idempotencyKey,
        sessionId: d.sessionId,
        itemId: d.itemId,
        answer: d.answer,
        flagged: d.flagged,
        timeSpentSecs: d.timeSpentSecs,
        lastModified: d.lastModified,
      }));

      this.http
        .post<SyncResult>(
          `${environment.apiUrl}/api/sessions/draft-assessments/sync`,
          payload
        )
        .subscribe({
          next: async () => {
            for (const draft of drafts) {
              if (draft.id !== undefined) {
                await db.draftAssessments.update(draft.id, { syncStatus: 'SYNCED' });
              }
            }
            resolve();
          },
          error: () => resolve(),
        });
    });
  }

  private async syncPendingSessions(): Promise<void> {
    const pending: LocalSession[] = await db.sessions
      .where('syncStatus')
      .equals('PENDING')
      .toArray();

    if (pending.length === 0) {
      return;
    }

    for (let i = 0; i < pending.length; i += BATCH_SIZE) {
      const batch = pending.slice(i, i + BATCH_SIZE);
      await this.syncBatch(batch);
    }
  }

  private async syncBatch(sessions: LocalSession[]): Promise<void> {
    return new Promise<void>((resolve) => {
      const payload = sessions.map((s) => ({
        idempotencyKey: s.idempotencyKey,
        courseId: s.courseId,
        status: s.status,
        restTimerSecs: s.restTimerSecs,
        startedAt: s.startedAt,
        completedAt: s.completedAt ?? null,
        activities: s.activities,
        clientUpdatedAt: s.updatedAt,
      }));

      this.http
        .post<SyncResult>(`${environment.apiUrl}/api/sessions/sync`, payload)
        .subscribe({
          next: async (result) => {
            const rejectedSet = new Set(result.rejectedKeys ?? []);
            for (const session of sessions) {
              if (rejectedSet.has(session.idempotencyKey)) {
                await db.sessions.update(session.idempotencyKey, {
                  syncStatus: 'CONFLICT',
                });
              } else {
                await db.sessions.update(session.idempotencyKey, {
                  syncStatus: 'SYNCED',
                });
              }
            }
            resolve();
          },
          error: () => resolve(),
        });
    });
  }
}
