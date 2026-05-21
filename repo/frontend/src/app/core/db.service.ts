import Dexie, { Table } from 'dexie';
import { Injectable } from '@angular/core';

export interface LocalSession {
  idempotencyKey: string;
  courseId: string;
  status: string;
  restTimerSecs: number;
  startedAt: string;
  completedAt?: string;
  activities: { activityRef: string; completed: boolean }[];
  syncStatus: 'PENDING' | 'SYNCED' | 'CONFLICT';
  updatedAt: string;
}

export interface DraftAssessment {
  id?: number;
  sessionId: string;
  itemId: string;
  answer: string;
  flagged: boolean;
  timeSpentSecs: number;
  idempotencyKey: string;
  syncStatus: 'PENDING' | 'SYNCED' | 'CONFLICT';
  lastModified: string;
}

export class MeridianDb extends Dexie {
  sessions!: Table<LocalSession, string>;
  draftAssessments!: Table<DraftAssessment, number>;

  constructor() {
    super('MeridianDb');
    this.version(1).stores({
      sessions: 'idempotencyKey, syncStatus, courseId',
      draftAssessments: '++id, sessionId, itemId',
    });
    this.version(2).stores({
      sessions: 'idempotencyKey, syncStatus, courseId',
      draftAssessments: '++id, &idempotencyKey, sessionId, itemId, syncStatus',
    });
  }
}

export const db = new MeridianDb();

@Injectable({ providedIn: 'root' })
export class DbService {
  readonly db = db;
}
