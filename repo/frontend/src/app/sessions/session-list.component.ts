import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../core/api.service';

export interface SessionSummary {
  id: string;
  courseId: string;
  courseName: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'INTERRUPTED' | 'COMPLETED' | 'VERIFIED';
  startedAt: string;
  completedAt?: string;
}

@Component({
  selector: 'app-session-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatCardModule,
  ],
  template: `
    <div class="session-list-container">
      <div class="header-row">
        <h1 class="page-title">My Sessions</h1>
        <a mat-raised-button color="primary" routerLink="/sessions/new">
          <mat-icon>add</mat-icon>
          New Session
        </a>
      </div>

      <div *ngIf="loading" class="loading-center">
        <mat-spinner diameter="48"></mat-spinner>
      </div>

      <ng-container *ngIf="!loading">
        <div class="empty-state" *ngIf="sessions.length === 0">
          <mat-icon>fitness_center</mat-icon>
          <p>No sessions yet. Start your first session.</p>
          <a mat-raised-button color="primary" routerLink="/sessions/new">Start Session</a>
        </div>

        <mat-card *ngIf="sessions.length > 0">
          <table mat-table [dataSource]="sessions" class="full-width-table">
            <ng-container matColumnDef="courseName">
              <th mat-header-cell *matHeaderCellDef>Course</th>
              <td mat-cell *matCellDef="let row">{{ row.courseName }}</td>
            </ng-container>

            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let row">
                <mat-chip [ngClass]="getStatusClass(row.status)" disableRipple>
                  {{ row.status | titlecase }}
                </mat-chip>
              </td>
            </ng-container>

            <ng-container matColumnDef="startedAt">
              <th mat-header-cell *matHeaderCellDef>Started At</th>
              <td mat-cell *matCellDef="let row">
                {{ row.startedAt | date: 'medium' }}
              </td>
            </ng-container>

            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let row">
                <a
                  mat-stroked-button
                  color="primary"
                  [routerLink]="['/sessions', row.id]"
                  *ngIf="isResumable(row.status)"
                  class="action-btn"
                >
                  <mat-icon>play_arrow</mat-icon>
                  Continue
                </a>
                <a
                  mat-stroked-button
                  [routerLink]="['/sessions', row.id]"
                  *ngIf="!isResumable(row.status)"
                  class="action-btn"
                >
                  <mat-icon>visibility</mat-icon>
                  View
                </a>
              </td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
          </table>
        </mat-card>

        <div class="error-banner" *ngIf="errorMessage" role="alert">
          {{ errorMessage }}
        </div>
      </ng-container>
    </div>
  `,
  styles: [
    `
      .session-list-container {
        max-width: 960px;
        margin: 0 auto;
      }
      .header-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 24px;
      }
      .page-title {
        font-size: 28px;
        font-weight: 500;
        margin: 0;
      }
      .loading-center {
        display: flex;
        justify-content: center;
        padding: 48px;
      }
      .full-width-table {
        width: 100%;
      }
      .action-btn {
        margin-right: 8px;
      }
      .empty-state {
        text-align: center;
        padding: 48px;
        color: rgba(0, 0, 0, 0.4);
        mat-icon {
          font-size: 48px;
          height: 48px;
          width: 48px;
          margin-bottom: 16px;
        }
      }
      .error-banner {
        background: #f44336;
        color: white;
        padding: 8px 16px;
        border-radius: 4px;
        margin-top: 12px;
      }
      .status-pending {
        background: #fff3e0 !important;
        color: #e65100 !important;
      }
      .status-in-progress {
        background: #e3f2fd !important;
        color: #1565c0 !important;
      }
      .status-interrupted {
        background: #fce4ec !important;
        color: #c62828 !important;
      }
      .status-completed {
        background: #e8f5e9 !important;
        color: #2e7d32 !important;
      }
      .status-verified {
        background: #f3e5f5 !important;
        color: #6a1b9a !important;
      }
    `,
  ],
})
export class SessionListComponent implements OnInit {
  sessions: SessionSummary[] = [];
  displayedColumns = ['courseName', 'status', 'startedAt', 'actions'];
  loading = false;
  errorMessage = '';

  constructor(
    private apiService: ApiService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadSessions();
  }

  isResumable(status: string): boolean {
    return status === 'IN_PROGRESS' || status === 'INTERRUPTED';
  }

  getStatusClass(status: string): string {
    return `status-${status.toLowerCase().replace('_', '-')}`;
  }

  private loadSessions(): void {
    this.loading = true;
    // GET /sessions returns a Spring Page ({ content: [...] }); the items carry courseId but no
    // courseName, so resolve names from the course catalog (best-effort — a courses failure must
    // not hide the sessions). Accept a bare array too so unit-test stubs work either way.
    forkJoin({
      sessions: this.apiService.get<SessionSummary[] | { content: SessionSummary[] }>('/sessions'),
      courses: this.apiService
        .get<any[] | { content: any[] }>('/courses')
        .pipe(catchError(() => of({ content: [] }))),
    }).subscribe({
      next: ({ sessions, courses }) => {
        const sList: any[] = Array.isArray(sessions) ? sessions : sessions.content ?? [];
        const cList: any[] = Array.isArray(courses) ? courses : courses.content ?? [];
        const nameById = new Map<string, string>(cList.map((c) => [c.id, c.title]));
        this.sessions = sList.map((s) => ({
          ...s,
          courseName: s.courseName ?? nameById.get(s.courseId) ?? s.courseId,
        }));
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Failed to load sessions.';
        this.cdr.markForCheck();
      },
    });
  }
}
