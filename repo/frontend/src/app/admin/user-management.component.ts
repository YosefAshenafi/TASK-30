import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ApiService } from '../core/api.service';

interface PendingUser {
  userId: string;
  username: string;
  createdAt: string;
  requestedRole: string;
}

interface UserRecord {
  userId: string;
  username: string;
  role: string;
  status: string;
  createdAt: string;
}

const ROLES = ['STUDENT', 'FACULTY_MENTOR', 'CORPORATE_MENTOR', 'ADMINISTRATOR'];

@Component({
  selector: 'app-user-management',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatTabsModule,
    MatTableModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatFormFieldModule,
    MatIconModule,
    MatSnackBarModule,
    MatPaginatorModule,
  ],
  template: `
    <div class="um-container">
      <h1 class="page-title">User Management</h1>

      <mat-tab-group animationDuration="200ms">

        <!-- Tab 1: Pending Approvals -->
        <mat-tab label="Pending Approvals">
          <div class="tab-content">
            <div *ngIf="loadingPending" class="loading-center">
              <mat-spinner diameter="40"></mat-spinner>
            </div>
            <div class="empty-state" *ngIf="!loadingPending && pendingUsers.length === 0">
              <mat-icon>check_circle</mat-icon>
              <p>No pending approvals.</p>
            </div>
            <mat-card *ngIf="!loadingPending && pendingUsers.length > 0">
              <table mat-table [dataSource]="pendingUsers" class="full-width-table">
                <ng-container matColumnDef="username">
                  <th mat-header-cell *matHeaderCellDef>Username</th>
                  <td mat-cell *matCellDef="let r">{{ r.username }}</td>
                </ng-container>
                <ng-container matColumnDef="requestedRole">
                  <th mat-header-cell *matHeaderCellDef>Requested Role</th>
                  <td mat-cell *matCellDef="let r">{{ r.requestedRole }}</td>
                </ng-container>
                <ng-container matColumnDef="createdAt">
                  <th mat-header-cell *matHeaderCellDef>Requested At</th>
                  <td mat-cell *matCellDef="let r">{{ r.createdAt | date: 'medium' }}</td>
                </ng-container>
                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef>Actions</th>
                  <td mat-cell *matCellDef="let r">
                    <button
                      mat-raised-button
                      color="primary"
                      (click)="approveUser(r)"
                      [disabled]="processingUserId === r.userId"
                      class="action-btn"
                    >
                      <mat-spinner diameter="16" *ngIf="processingUserId === r.userId"></mat-spinner>
                      <mat-icon *ngIf="processingUserId !== r.userId">check</mat-icon>
                      Approve
                    </button>
                    <button
                      mat-stroked-button
                      color="warn"
                      (click)="rejectUser(r)"
                      [disabled]="processingUserId === r.userId"
                    >
                      <mat-icon>close</mat-icon>
                      Reject
                    </button>
                  </td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="pendingColumns"></tr>
                <tr mat-row *matRowDef="let r; columns: pendingColumns"></tr>
              </table>
            </mat-card>
          </div>
        </mat-tab>

        <!-- Tab 2: All Users -->
        <mat-tab label="All Users">
          <div class="tab-content">
            <div class="filter-row">
              <mat-form-field appearance="outline">
                <mat-label>Filter by Role</mat-label>
                <mat-select [(ngModel)]="roleFilter" (ngModelChange)="onRoleFilterChange()">
                  <mat-option value="">All Roles</mat-option>
                  <mat-option *ngFor="let role of roles" [value]="role">{{ role }}</mat-option>
                </mat-select>
              </mat-form-field>
            </div>
            <div *ngIf="loadingUsers" class="loading-center">
              <mat-spinner diameter="40"></mat-spinner>
            </div>
            <div class="empty-state" *ngIf="!loadingUsers && users.length === 0">
              <mat-icon>people</mat-icon>
              <p>No users found.</p>
            </div>
            <mat-card *ngIf="!loadingUsers && users.length > 0">
              <table mat-table [dataSource]="pagedUsers" class="full-width-table">
                <ng-container matColumnDef="username">
                  <th mat-header-cell *matHeaderCellDef>Username</th>
                  <td mat-cell *matCellDef="let r">{{ r.username }}</td>
                </ng-container>
                <ng-container matColumnDef="role">
                  <th mat-header-cell *matHeaderCellDef>Role</th>
                  <td mat-cell *matCellDef="let r">
                    <mat-select
                      [value]="r.role"
                      (selectionChange)="changeRole(r, $event.value)"
                      class="role-select"
                    >
                      <mat-option *ngFor="let role of roles" [value]="role">{{ role }}</mat-option>
                    </mat-select>
                  </td>
                </ng-container>
                <ng-container matColumnDef="status">
                  <th mat-header-cell *matHeaderCellDef>Status</th>
                  <td mat-cell *matCellDef="let r">{{ r.status }}</td>
                </ng-container>
                <ng-container matColumnDef="createdAt">
                  <th mat-header-cell *matHeaderCellDef>Created</th>
                  <td mat-cell *matCellDef="let r">{{ r.createdAt | date: 'mediumDate' }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="userColumns"></tr>
                <tr mat-row *matRowDef="let r; columns: userColumns"></tr>
              </table>
              <mat-paginator
                [length]="users.length"
                [pageSize]="pageSize"
                [pageSizeOptions]="[10, 25, 50]"
                (page)="onPage($event)"
                showFirstLastButtons
              ></mat-paginator>
            </mat-card>
          </div>
        </mat-tab>

      </mat-tab-group>

      <div class="error-banner" *ngIf="errorMessage" role="alert">{{ errorMessage }}</div>
    </div>
  `,
  styles: [
    `
      .um-container { max-width: 960px; margin: 0 auto; }
      .page-title { font-size: 28px; font-weight: 500; margin-bottom: 24px; }
      .tab-content { padding: 16px 0; }
      .loading-center { display: flex; justify-content: center; padding: 48px; }
      .full-width-table { width: 100%; }
      .action-btn { margin-right: 8px; }
      .filter-row { display: flex; gap: 12px; margin-bottom: 16px; }
      .role-select { min-width: 160px; }
      .empty-state { text-align: center; padding: 48px; color: rgba(0,0,0,0.4); }
      .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; }
      .error-banner { background: #f44336; color: white; padding: 8px 16px; border-radius: 4px; margin-top: 12px; }
    `,
  ],
})
export class UserManagementComponent implements OnInit {
  pendingUsers: PendingUser[] = [];
  users: UserRecord[] = [];
  pagedUsers: UserRecord[] = [];
  roles = ROLES;
  pendingColumns = ['username', 'requestedRole', 'createdAt', 'actions'];
  userColumns = ['username', 'role', 'status', 'createdAt'];

  loadingPending = false;
  loadingUsers = false;
  processingUserId: string | null = null;
  errorMessage = '';
  roleFilter = '';
  pageSize = 10;
  pageIndex = 0;

  constructor(
    private apiService: ApiService,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadPendingUsers();
    this.loadAllUsers();
  }

  approveUser(user: PendingUser): void {
    this.processingUserId = user.userId;
    this.apiService
      .post(`/admin/users/${user.userId}/approve`, {})
      .subscribe({
        next: () => {
          this.pendingUsers = this.pendingUsers.filter((u) => u.userId !== user.userId);
          this.processingUserId = null;
          this.snackBar.open(`${user.username} approved.`, 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
        error: () => {
          this.processingUserId = null;
          this.snackBar.open('Failed to approve user.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
      });
  }

  rejectUser(user: PendingUser): void {
    this.processingUserId = user.userId;
    this.apiService
      .post(`/admin/users/${user.userId}/reject`, {})
      .subscribe({
        next: () => {
          this.pendingUsers = this.pendingUsers.filter((u) => u.userId !== user.userId);
          this.processingUserId = null;
          this.snackBar.open(`${user.username} rejected.`, 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
        error: () => {
          this.processingUserId = null;
          this.snackBar.open('Failed to reject user.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
      });
  }

  changeRole(user: UserRecord, newRole: string): void {
    this.apiService
      .patch(`/admin/users/${user.userId}/role`, { role: newRole })
      .subscribe({
        next: () => {
          user.role = newRole;
          this.snackBar.open(`Role updated to ${newRole}.`, 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
        error: () => {
          this.snackBar.open('Failed to update role.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
      });
  }

  onRoleFilterChange(): void {
    this.pageIndex = 0;
    this.updatePagedUsers();
  }

  onPage(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.updatePagedUsers();
  }

  private updatePagedUsers(): void {
    const filtered = this.roleFilter
      ? this.users.filter((u) => u.role === this.roleFilter)
      : this.users;
    const start = this.pageIndex * this.pageSize;
    this.pagedUsers = filtered.slice(start, start + this.pageSize);
    this.cdr.markForCheck();
  }

  private loadPendingUsers(): void {
    this.loadingPending = true;
    this.apiService.get<PendingUser[]>('/admin/users/pending').subscribe({
      next: (d) => {
        this.pendingUsers = d;
        this.loadingPending = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingPending = false;
        this.errorMessage = 'Failed to load pending users.';
        this.cdr.markForCheck();
      },
    });
  }

  private loadAllUsers(): void {
    this.loadingUsers = true;
    this.apiService.get<UserRecord[]>('/admin/users').subscribe({
      next: (d) => {
        this.users = d;
        this.loadingUsers = false;
        this.updatePagedUsers();
      },
      error: () => {
        this.loadingUsers = false;
        this.errorMessage = 'Failed to load users.';
        this.cdr.markForCheck();
      },
    });
  }
}
