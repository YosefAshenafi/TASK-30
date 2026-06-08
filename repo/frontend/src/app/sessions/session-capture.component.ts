import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDividerModule } from '@angular/material/divider';
import { Subscription, Observable } from 'rxjs';
import { ApiService } from '../core/api.service';
import { DbService, LocalSession } from '../core/db.service';
import { SyncService } from '../core/sync.service';
import { v4 as uuidv4 } from 'uuid';

interface Activity {
  activityId?: string;
  activityRef: string;
  name: string;
  completed: boolean;
}

interface CourseOption {
  id: string;
  title: string;
}

interface SessionDetail {
  id: string;
  courseId: string;
  courseName: string;
  status: string;
  restTimerSecs: number;
  startedAt: string;
  completedAt?: string;
  activities: Activity[];
  idempotencyKey: string;
}

function restTimerRangeValidator(control: AbstractControl): ValidationErrors | null {
  const val = Number(control.value);
  if (isNaN(val) || val < 15 || val > 300) {
    return { outOfRange: true };
  }
  return null;
}

@Component({
  selector: 'app-session-capture',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDividerModule,
    FormsModule,
  ],
  template: `
    <div class="session-capture-container">
      <!-- Offline Banner: shown whenever the client is offline, in any session state
           (loading, course selection, error, or an active session). -->
      <div class="offline-banner" *ngIf="isOffline" role="alert">
        <mat-icon>wifi_off</mat-icon>
        Offline — changes saved locally and will sync when connection is restored.
      </div>

      <div *ngIf="loading" class="loading-center">
        <mat-spinner diameter="48"></mat-spinner>
      </div>

      <!-- Course selection (shown when starting a new session with >1 course) -->
      <mat-card class="course-picker-card" *ngIf="!loading && selectingCourse && !session">
        <mat-card-header>
          <mat-card-title>Start a Training Session</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <mat-form-field appearance="outline" class="course-select">
            <mat-label>Select a course</mat-label>
            <mat-select [(ngModel)]="selectedCourseId">
              <mat-option *ngFor="let c of courses" [value]="c.id">{{ c.title }}</mat-option>
            </mat-select>
          </mat-form-field>
          <button
            mat-raised-button
            color="primary"
            [disabled]="!selectedCourseId"
            (click)="startSelectedCourse()"
          >
            <mat-icon>play_arrow</mat-icon>
            Start Session
          </button>
        </mat-card-content>
      </mat-card>

      <div class="error-banner" *ngIf="!loading && !session && errorMessage" role="alert">
        {{ errorMessage }}
      </div>

      <ng-container *ngIf="!loading && session">
        <!-- Session Header -->
        <div class="session-header">
          <div>
            <h1 class="page-title">{{ session.courseName }}</h1>
            <p class="session-status">Status: {{ session.status }}</p>
          </div>
          <div class="timer-display">
            <mat-icon>timer</mat-icon>
            <span class="elapsed-time" data-testid="elapsed-timer">{{ formatElapsed(elapsed) }}</span>
          </div>
        </div>

        <!-- Rest Timer -->
        <mat-card class="rest-timer-card">
          <mat-card-header>
            <mat-card-title>Rest Timer</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <form [formGroup]="restTimerForm" class="rest-timer-form">
              <mat-form-field appearance="outline">
                <mat-label>Rest Duration (seconds)</mat-label>
                <input
                  matInput
                  type="number"
                  formControlName="restTimerSecs"
                  min="15"
                  max="300"
                />
                <mat-error *ngIf="restTimerForm.get('restTimerSecs')?.hasError('required')">
                  Required
                </mat-error>
                <mat-error *ngIf="restTimerForm.get('restTimerSecs')?.hasError('outOfRange')">
                  Must be between 15 and 300 seconds
                </mat-error>
              </mat-form-field>
              <div class="rest-countdown" *ngIf="restCountdown !== null">
                <span class="countdown-value">{{ restCountdown }}s</span>
                <button mat-stroked-button (click)="resetRestTimer()" type="button">
                  <mat-icon>refresh</mat-icon>
                  Reset
                </button>
              </div>
              <button
                mat-raised-button
                color="accent"
                type="button"
                (click)="startRestTimer()"
                [disabled]="restTimerForm.invalid || restTimerRunning"
              >
                <mat-icon>hourglass_top</mat-icon>
                Start Rest
              </button>
            </form>
          </mat-card-content>
        </mat-card>

        <!-- Activity Checklist -->
        <mat-card class="activities-card">
          <mat-card-header>
            <mat-card-title>Activities</mat-card-title>
          </mat-card-header>
          <mat-card-content>
            <div class="activity-list">
              <div
                class="activity-item"
                *ngFor="let activity of session.activities; let i = index"
              >
                <mat-checkbox
                  [checked]="activity.completed"
                  (change)="updateActivity(i, $event.checked)"
                  [disabled]="session.status === 'COMPLETED'"
                  color="primary"
                >
                  {{ activity.name || activity.activityRef }}
                </mat-checkbox>
              </div>
            </div>
            <div class="empty-state" *ngIf="session.activities.length === 0">
              <p>No activities defined for this course.</p>
            </div>
          </mat-card-content>
        </mat-card>

        <!-- Actions -->
        <div class="action-row" *ngIf="session.status !== 'COMPLETED'">
          <button
            mat-raised-button
            color="primary"
            (click)="saveAndComplete()"
            [disabled]="completing"
          >
            <mat-spinner diameter="20" *ngIf="completing"></mat-spinner>
            <mat-icon *ngIf="!completing">check_circle</mat-icon>
            <span>{{ completing ? 'Saving...' : 'Save & Complete' }}</span>
          </button>
          <a mat-stroked-button routerLink="/sessions" class="back-btn">
            <mat-icon>arrow_back</mat-icon>
            Back to Sessions
          </a>
        </div>

        <div class="action-row" *ngIf="session.status === 'COMPLETED'">
          <a mat-raised-button routerLink="/sessions">
            <mat-icon>arrow_back</mat-icon>
            Back to Sessions
          </a>
        </div>

        <div class="error-banner" *ngIf="errorMessage" role="alert">
          {{ errorMessage }}
        </div>
      </ng-container>

      <div class="empty-state" *ngIf="!loading && !session">
        <mat-icon>error_outline</mat-icon>
        <p>Session not found.</p>
        <a mat-raised-button routerLink="/sessions">Back to Sessions</a>
      </div>
    </div>
  `,
  styles: [
    `
      .session-capture-container {
        max-width: 800px;
        margin: 0 auto;
      }
      .loading-center {
        display: flex;
        justify-content: center;
        padding: 48px;
      }
      .offline-banner {
        background: #ff9800;
        color: white;
        padding: 12px 16px;
        border-radius: 4px;
        margin-bottom: 16px;
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 14px;
      }
      .session-header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        margin-bottom: 24px;
      }
      .page-title {
        font-size: 24px;
        font-weight: 500;
        margin: 0 0 4px;
      }
      .session-status {
        font-size: 13px;
        color: rgba(0, 0, 0, 0.54);
        margin: 0;
      }
      .timer-display {
        display: flex;
        align-items: center;
        gap: 8px;
        background: #3f51b5;
        color: white;
        padding: 12px 20px;
        border-radius: 8px;
      }
      .elapsed-time {
        font-size: 28px;
        font-weight: 700;
        font-family: 'Courier New', monospace;
      }
      .rest-timer-card {
        margin-bottom: 16px;
      }
      .rest-timer-form {
        display: flex;
        align-items: center;
        gap: 16px;
        flex-wrap: wrap;
        padding-top: 8px;
      }
      .rest-countdown {
        display: flex;
        align-items: center;
        gap: 8px;
      }
      .countdown-value {
        font-size: 24px;
        font-weight: 700;
        color: #ff9800;
      }
      .activities-card {
        margin-bottom: 16px;
      }
      .activity-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        padding: 8px 0;
      }
      .activity-item {
        padding: 4px 0;
      }
      .action-row {
        display: flex;
        gap: 16px;
        align-items: center;
        margin-top: 16px;
        flex-wrap: wrap;
      }
      .back-btn {
        margin-left: 8px;
      }
      .error-banner {
        background: #f44336;
        color: white;
        padding: 8px 16px;
        border-radius: 4px;
        margin-top: 12px;
      }
      .empty-state {
        text-align: center;
        padding: 48px;
        color: rgba(0, 0, 0, 0.4);
        mat-icon {
          font-size: 48px;
          height: 48px;
          width: 48px;
        }
      }
    `,
  ],
})
export class SessionCaptureComponent implements OnInit, OnDestroy {
  session: SessionDetail | null = null;
  loading = false;
  completing = false;
  errorMessage = '';
  isOffline = false;

  // New-session course selection
  selectingCourse = false;
  courses: CourseOption[] = [];
  selectedCourseId: string | null = null;

  elapsed = 0;
  restCountdown: number | null = null;
  restTimerRunning = false;

  restTimerForm: FormGroup;

  private elapsedIntervalId: ReturnType<typeof setInterval> | null = null;
  private restIntervalId: ReturnType<typeof setInterval> | null = null;
  private offlineSub: Subscription | null = null;
  private sessionId: string | null = null;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private dbService: DbService,
    private syncService: SyncService,
    private cdr: ChangeDetectorRef
  ) {
    this.restTimerForm = this.fb.group({
      restTimerSecs: [60, [Validators.required, restTimerRangeValidator]],
    });
  }

  ngOnInit(): void {
    this.offlineSub = this.syncService.offlineMode$.subscribe((offline) => {
      this.isOffline = offline;
      this.cdr.markForCheck();
    });
    this.isOffline = this.syncService.isOffline();

    this.sessionId = this.route.snapshot.paramMap.get('id');

    if (this.sessionId) {
      this.loadExistingSession(this.sessionId);
    } else {
      this.startNewSession();
    }
  }

  ngOnDestroy(): void {
    this.stopElapsedTimer();
    this.stopRestTimer();
    this.offlineSub?.unsubscribe();
  }

  formatElapsed(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return [h, m, s].map((v) => String(v).padStart(2, '0')).join(':');
  }

  updateActivity(index: number, checked: boolean): void {
    if (!this.session) return;
    this.session.activities[index].completed = checked;

    if (this.isOffline) {
      this.saveToIndexedDB();
    } else {
      this.apiService
        .put<SessionDetail>(`/sessions/${this.session.id}`, {
          activities: this.session.activities.map((a) => ({
            activityId: a.activityId,
            completed: a.completed,
          })),
        })
        .subscribe({
          error: () => {
            this.saveToIndexedDB();
          },
        });
    }
  }

  startRestTimer(): void {
    if (this.restTimerForm.invalid) return;
    const secs: number = this.restTimerForm.get('restTimerSecs')!.value;
    this.restCountdown = secs;
    this.restTimerRunning = true;
    this.stopRestTimer();
    this.restIntervalId = setInterval(() => {
      if (this.restCountdown !== null && this.restCountdown > 0) {
        this.restCountdown--;
        this.cdr.markForCheck();
      } else {
        this.stopRestTimer();
        this.restTimerRunning = false;
        this.cdr.markForCheck();
      }
    }, 1000);
  }

  resetRestTimer(): void {
    this.stopRestTimer();
    this.restTimerRunning = false;
    this.restCountdown = null;
    this.cdr.markForCheck();
  }

  saveAndComplete(): void {
    if (!this.session) return;
    this.completing = true;

    this.apiService
      .post<SessionDetail>(`/sessions/${this.session.id}/complete`, {})
      .subscribe({
        next: () => {
          this.completing = false;
          this.router.navigate(['/sessions']);
        },
        error: () => {
          this.completing = false;
          this.errorMessage = 'Failed to complete session. Please try again.';
          this.cdr.markForCheck();
        },
      });
  }

  private loadExistingSession(id: string): void {
    this.loading = true;

    this.apiService.get<any>(`/sessions/${id}`).subscribe({
      next: (data) => {
        this.session = this.mapResponseToSession(data, '');
        this.loading = false;
        this.restTimerForm.patchValue({ restTimerSecs: data.restTimerSecs });
        // Resolve a human-readable course title for the header.
        this.apiService.get<{ title: string }>(`/courses/${data.courseId}`).subscribe({
          next: (c) => {
            if (this.session) this.session.courseName = c?.title ?? '';
            this.cdr.markForCheck();
          },
          error: () => {},
        });
        this.startElapsedTimer();
        this.cdr.markForCheck();
      },
      error: async () => {
        const local = await this.dbService.db.sessions.get(id);
        if (local) {
          this.session = this.mapLocalToDetail(local);
          this.restTimerForm.patchValue({ restTimerSecs: local.restTimerSecs });
          this.startElapsedTimer();
        }
        this.loading = false;
        this.cdr.markForCheck();
      },
    });
  }

  // Step 1 of starting a session: load the courses the student can train on so a
  // course can be chosen. A session must be tied to a course (the backend requires
  // courseId), and the activity checklist is derived from that course's items.
  private startNewSession(): void {
    this.loading = true;
    this.apiService
      .get<{ content: CourseOption[] }>('/courses')
      .subscribe({
        next: (page) => {
          this.courses = (page?.content ?? []).map((c) => ({ id: c.id, title: c.title }));
          this.loading = false;
          if (this.courses.length === 1) {
            // Only one course available — start immediately for a smooth flow.
            this.createSessionForCourse(this.courses[0].id, this.courses[0].title);
          } else if (this.courses.length > 1) {
            this.selectingCourse = true;
          } else {
            this.errorMessage = 'No courses are available to start a session.';
          }
          this.cdr.markForCheck();
        },
        error: () => {
          this.loading = false;
          this.errorMessage = 'Failed to load courses. Please try again.';
          this.cdr.markForCheck();
        },
      });
  }

  // Invoked by the course-picker "Start Session" button.
  startSelectedCourse(): void {
    if (!this.selectedCourseId) return;
    const course = this.courses.find((c) => c.id === this.selectedCourseId);
    this.selectingCourse = false;
    this.createSessionForCourse(this.selectedCourseId, course?.title ?? '');
  }

  // Step 2: create the session on the server for the chosen course.
  private createSessionForCourse(courseId: string, courseName: string): void {
    this.loading = true;
    this.apiService
      .post<SessionDetail>('/sessions', { courseId })
      .subscribe({
        next: (data) => {
          this.session = this.mapResponseToSession(data, courseName);
          this.loading = false;
          this.startElapsedTimer();
          this.router.navigate(['/sessions', data.id], { replaceUrl: true });
          this.cdr.markForCheck();
        },
        error: () => {
          // Offline / server-unreachable fallback: keep a local draft to sync later.
          const idempotencyKey = uuidv4();
          const newLocalSession: LocalSession = {
            idempotencyKey,
            courseId,
            status: 'IN_PROGRESS',
            restTimerSecs: 60,
            startedAt: new Date().toISOString(),
            activities: [],
            syncStatus: 'PENDING',
            updatedAt: new Date().toISOString(),
          };
          this.dbService.db.sessions.put(newLocalSession);
          this.session = this.mapLocalToDetail(newLocalSession);
          this.session.courseName = courseName || 'Offline Session';
          this.loading = false;
          this.startElapsedTimer();
          this.cdr.markForCheck();
        },
      });
  }

  // Maps a server SessionResponse (which carries activities as {activityId,name,completed})
  // into the component's SessionDetail shape used by the template.
  private mapResponseToSession(data: any, courseName: string): SessionDetail {
    return {
      id: data.id,
      courseId: data.courseId,
      courseName: courseName || data.courseName || '',
      status: data.status,
      restTimerSecs: data.restTimerSecs,
      startedAt: data.startedAt,
      completedAt: data.completedAt,
      idempotencyKey: data.idempotencyKey ?? '',
      activities: (data.activities ?? []).map((a: any) => ({
        activityId: a.activityId,
        activityRef: a.name,
        name: a.name,
        completed: a.completed,
      })),
    };
  }

  private saveToIndexedDB(): void {
    if (!this.session) return;
    const localSession: LocalSession = {
      idempotencyKey: this.session.idempotencyKey || this.session.id,
      courseId: this.session.courseId,
      status: this.session.status,
      restTimerSecs: this.restTimerForm.get('restTimerSecs')!.value,
      startedAt: this.session.startedAt,
      completedAt: this.session.completedAt,
      activities: this.session.activities.map((a) => ({
        activityRef: a.activityRef,
        completed: a.completed,
      })),
      syncStatus: 'PENDING',
      updatedAt: new Date().toISOString(),
    };
    this.dbService.db.sessions.put(localSession);
  }

  private mapLocalToDetail(local: LocalSession): SessionDetail {
    return {
      id: local.idempotencyKey,
      courseId: local.courseId,
      courseName: local.courseId || 'Offline Session',
      status: local.status,
      restTimerSecs: local.restTimerSecs,
      startedAt: local.startedAt,
      completedAt: local.completedAt,
      activities: local.activities.map((a) => ({
        activityRef: a.activityRef,
        name: a.activityRef,
        completed: a.completed,
      })),
      idempotencyKey: local.idempotencyKey,
    };
  }

  private startElapsedTimer(): void {
    this.stopElapsedTimer();
    this.elapsedIntervalId = setInterval(() => {
      this.elapsed++;
      this.cdr.markForCheck();
    }, 1000);
  }

  private stopElapsedTimer(): void {
    if (this.elapsedIntervalId !== null) {
      clearInterval(this.elapsedIntervalId);
      this.elapsedIntervalId = null;
    }
  }

  private stopRestTimer(): void {
    if (this.restIntervalId !== null) {
      clearInterval(this.restIntervalId);
      this.restIntervalId = null;
    }
  }
}
