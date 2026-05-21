import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatGridListModule } from '@angular/material/grid-list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';

interface DashboardSummary {
  pendingApprovalsCount: number;
  recentAnomaliesCount: number;
  totalSessions: number;
  activeSessions: number;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatGridListModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatDividerModule,
  ],
  template: `
    <div class="dashboard-container">
      <h1 class="page-title">Dashboard</h1>

      <div *ngIf="loading" class="loading-center">
        <mat-spinner diameter="48"></mat-spinner>
      </div>

      <ng-container *ngIf="!loading">
        <!-- Student View -->
        <ng-container *ngIf="isStudent()">
          <mat-card class="welcome-card">
            <mat-card-header>
              <mat-icon mat-card-avatar color="primary">fitness_center</mat-icon>
              <mat-card-title>Welcome back!</mat-card-title>
              <mat-card-subtitle>Track your training progress</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <p>Ready to continue your training journey? Start or continue a session below.</p>
            </mat-card-content>
            <mat-card-actions>
              <a mat-raised-button color="primary" routerLink="/sessions">
                <mat-icon>list</mat-icon>
                My Sessions
              </a>
              <a mat-raised-button color="accent" routerLink="/sessions/new">
                <mat-icon>add</mat-icon>
                New Session
              </a>
            </mat-card-actions>
          </mat-card>

          <mat-card *ngIf="summary" class="stats-card">
            <mat-card-content>
              <div class="stat-row">
                <div class="stat-item">
                  <span class="stat-value">{{ summary.totalSessions }}</span>
                  <span class="stat-label">Total Sessions</span>
                </div>
                <mat-divider vertical></mat-divider>
                <div class="stat-item">
                  <span class="stat-value">{{ summary.activeSessions }}</span>
                  <span class="stat-label">Active Sessions</span>
                </div>
              </div>
            </mat-card-content>
          </mat-card>
        </ng-container>

        <!-- Mentor / Admin View -->
        <ng-container *ngIf="isMentorOrAdmin()">
          <mat-card class="welcome-card">
            <mat-card-header>
              <mat-icon mat-card-avatar color="primary">analytics</mat-icon>
              <mat-card-title>Training Analytics Overview</mat-card-title>
              <mat-card-subtitle>Monitor trainee progress and system health</mat-card-subtitle>
            </mat-card-header>
          </mat-card>

          <div class="summary-grid" *ngIf="summary; else emptyState">
            <mat-card class="summary-card warn-card" *ngIf="isAdmin()">
              <mat-card-content>
                <div class="summary-icon-row">
                  <mat-icon color="warn">pending_actions</mat-icon>
                  <span class="summary-count">{{ summary.pendingApprovalsCount }}</span>
                </div>
                <p class="summary-label">Pending Approvals</p>
                <a mat-stroked-button routerLink="/admin/users">Review</a>
              </mat-card-content>
            </mat-card>

            <mat-card class="summary-card">
              <mat-card-content>
                <div class="summary-icon-row">
                  <mat-icon color="accent">warning_amber</mat-icon>
                  <span class="summary-count">{{ summary.recentAnomaliesCount }}</span>
                </div>
                <p class="summary-label">Recent Anomalies</p>
                <a mat-stroked-button routerLink="/analytics">Investigate</a>
              </mat-card-content>
            </mat-card>

            <mat-card class="summary-card">
              <mat-card-content>
                <div class="summary-icon-row">
                  <mat-icon>people</mat-icon>
                  <span class="summary-count">{{ summary.totalSessions }}</span>
                </div>
                <p class="summary-label">Total Sessions</p>
                <a mat-stroked-button routerLink="/reports">View Reports</a>
              </mat-card-content>
            </mat-card>
          </div>

          <ng-template #emptyState>
            <div class="empty-state" *ngIf="!loading">
              <mat-icon>inbox</mat-icon>
              <p>No summary data available.</p>
            </div>
          </ng-template>
        </ng-container>

        <div class="error-banner" *ngIf="errorMessage" role="alert">
          {{ errorMessage }}
        </div>
      </ng-container>
    </div>
  `,
  styles: [
    `
      .dashboard-container {
        max-width: 960px;
        margin: 0 auto;
      }
      .page-title {
        font-size: 28px;
        font-weight: 500;
        margin-bottom: 24px;
        color: rgba(0, 0, 0, 0.87);
      }
      .loading-center {
        display: flex;
        justify-content: center;
        padding: 48px;
      }
      .welcome-card {
        margin-bottom: 24px;
      }
      .stats-card {
        margin-bottom: 24px;
      }
      .stat-row {
        display: flex;
        align-items: center;
        gap: 32px;
        padding: 16px 0;
      }
      .stat-item {
        display: flex;
        flex-direction: column;
        align-items: center;
      }
      .stat-value {
        font-size: 32px;
        font-weight: 600;
        color: #3f51b5;
      }
      .stat-label {
        font-size: 13px;
        color: rgba(0, 0, 0, 0.54);
        margin-top: 4px;
      }
      .summary-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
        gap: 16px;
        margin-bottom: 24px;
      }
      .summary-card {
        text-align: center;
      }
      .summary-icon-row {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 12px;
        margin-bottom: 8px;
      }
      .summary-count {
        font-size: 36px;
        font-weight: 700;
      }
      .summary-label {
        font-size: 13px;
        color: rgba(0, 0, 0, 0.54);
        margin-bottom: 12px;
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
      .error-banner {
        background: #f44336;
        color: white;
        padding: 8px 16px;
        border-radius: 4px;
        margin-top: 12px;
      }
    `,
  ],
})
export class DashboardComponent implements OnInit {
  loading = false;
  summary: DashboardSummary | null = null;
  errorMessage = '';

  constructor(
    private authService: AuthService,
    private apiService: ApiService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadSummary();
  }

  isStudent(): boolean {
    return this.authService.hasRole('STUDENT');
  }

  isMentorOrAdmin(): boolean {
    return this.authService.hasRole(
      'FACULTY_MENTOR',
      'CORPORATE_MENTOR',
      'ADMINISTRATOR'
    );
  }

  isAdmin(): boolean {
    return this.authService.hasRole('ADMINISTRATOR');
  }

  private loadSummary(): void {
    this.loading = true;
    const endpoint = this.isStudent()
      ? '/dashboard/student-summary'
      : '/dashboard/summary';

    this.apiService.get<DashboardSummary>(endpoint).subscribe({
      next: (data) => {
        this.summary = data;
        this.loading = false;
        this.cdr.markForCheck();
      },
      error: (_err: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = 'Failed to load dashboard data.';
        this.cdr.markForCheck();
      },
    });
  }
}
