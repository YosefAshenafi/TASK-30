import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ApiService } from '../core/api.service';

interface PendingUser {
  id: string;
  username: string;
  createdAt: string;
  requestedRole: string;
}

interface UserRecord {
  id: string;
  username: string;
  role: string;
  status: string;
  createdAt: string;
}

const ROLES = ['STUDENT', 'FACULTY_MENTOR', 'CORPORATE_MENTOR', 'ADMINISTRATOR'];
const STATUSES = ['ACTIVE', 'PENDING', 'LOCKED', 'REJECTED'];
// Mirrors the backend RegisterRequest/CreateUserRequest password policy.
const PASSWORD_PATTERN = /^(?=.*[0-9])(?=.*[!@#$%^&*]).*$/;

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
    MatInputModule,
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
                      [disabled]="processingUserId === r.id"
                      class="action-btn"
                    >
                      <mat-spinner diameter="16" *ngIf="processingUserId === r.id"></mat-spinner>
                      <mat-icon *ngIf="processingUserId !== r.userId">check</mat-icon>
                      Approve
                    </button>
                    <button
                      mat-stroked-button
                      color="warn"
                      (click)="rejectUser(r)"
                      [disabled]="processingUserId === r.id"
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

            <!-- Create User -->
            <mat-card class="create-card">
              <div class="create-header">
                <span class="create-title">Add User</span>
                <button mat-stroked-button color="primary" type="button" (click)="toggleCreateForm()">
                  <mat-icon>{{ showCreateForm ? 'expand_less' : 'person_add' }}</mat-icon>
                  {{ showCreateForm ? 'Cancel' : 'New User' }}
                </button>
              </div>
              <form
                *ngIf="showCreateForm"
                [formGroup]="createForm"
                (ngSubmit)="createUser()"
                class="create-form"
              >
                <mat-form-field appearance="outline">
                  <mat-label>Username</mat-label>
                  <input matInput formControlName="username" autocomplete="off" />
                  <mat-error *ngIf="createForm.get('username')?.hasError('required')">
                    Username is required
                  </mat-error>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Password</mat-label>
                  <input matInput type="password" formControlName="password" autocomplete="new-password" />
                  <mat-error *ngIf="createForm.get('password')?.hasError('required')">
                    Password is required
                  </mat-error>
                  <mat-error *ngIf="createForm.get('password')?.hasError('minlength')">
                    At least 12 characters
                  </mat-error>
                  <mat-error *ngIf="createForm.get('password')?.hasError('pattern')">
                    Must include a number and a symbol
                  </mat-error>
                </mat-form-field>
                <mat-form-field appearance="outline">
                  <mat-label>Role</mat-label>
                  <mat-select formControlName="role">
                    <mat-option *ngFor="let role of roles" [value]="role">{{ role }}</mat-option>
                  </mat-select>
                </mat-form-field>
                <button
                  mat-raised-button
                  color="primary"
                  type="submit"
                  [disabled]="createForm.invalid || creatingUser"
                  class="create-submit"
                >
                  <mat-spinner diameter="20" *ngIf="creatingUser"></mat-spinner>
                  <mat-icon *ngIf="!creatingUser">check</mat-icon>
                  Create
                </button>
              </form>
            </mat-card>

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
                  <td mat-cell *matCellDef="let r">
                    <mat-select
                      [value]="r.status"
                      (selectionChange)="changeStatus(r, $event.value)"
                      class="status-select"
                    >
                      <mat-option *ngFor="let s of statuses" [value]="s">{{ s }}</mat-option>
                    </mat-select>
                  </td>
                </ng-container>
                <ng-container matColumnDef="createdAt">
                  <th mat-header-cell *matHeaderCellDef>Created</th>
                  <td mat-cell *matCellDef="let r">{{ r.createdAt | date: 'mediumDate' }}</td>
                </ng-container>
                <ng-container matColumnDef="actions">
                  <th mat-header-cell *matHeaderCellDef>Actions</th>
                  <td mat-cell *matCellDef="let r">
                    <button
                      mat-icon-button
                      color="warn"
                      (click)="deleteUser(r)"
                      [disabled]="deletingUserId === r.id"
                      aria-label="Delete user"
                    >
                      <mat-spinner diameter="18" *ngIf="deletingUserId === r.id"></mat-spinner>
                      <mat-icon *ngIf="deletingUserId !== r.id">delete</mat-icon>
                    </button>
                  </td>
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
      .status-select { min-width: 130px; }
      .create-card { margin-bottom: 16px; padding: 16px; }
      .create-header { display: flex; align-items: center; justify-content: space-between; }
      .create-title { font-size: 16px; font-weight: 500; }
      .create-form { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; padding-top: 16px; }
      .create-form mat-form-field { min-width: 200px; }
      .create-submit { margin-top: 6px; }
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
  statuses = STATUSES;
  pendingColumns = ['username', 'requestedRole', 'createdAt', 'actions'];
  userColumns = ['username', 'role', 'status', 'createdAt', 'actions'];

  loadingPending = false;
  loadingUsers = false;
  processingUserId: string | null = null;
  deletingUserId: string | null = null;
  creatingUser = false;
  showCreateForm = false;
  errorMessage = '';
  roleFilter = '';
  pageSize = 10;
  pageIndex = 0;

  createForm: FormGroup;

  constructor(
    private apiService: ApiService,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef,
    private fb: FormBuilder
  ) {
    this.createForm = this.fb.group({
      username: ['', [Validators.required]],
      password: [
        '',
        [Validators.required, Validators.minLength(12), Validators.pattern(PASSWORD_PATTERN)],
      ],
      role: ['STUDENT', [Validators.required]],
    });
  }

  ngOnInit(): void {
    this.loadPendingUsers();
    this.loadAllUsers();
  }

  approveUser(user: PendingUser): void {
    this.processingUserId = user.id;
    this.apiService
      .put(`/admin/users/${user.id}/approve`, {})
      .subscribe({
        next: () => {
          this.pendingUsers = this.pendingUsers.filter((u) => u.id !== user.id);
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
    this.processingUserId = user.id;
    this.apiService
      .put(`/admin/users/${user.id}/reject`, {})
      .subscribe({
        next: () => {
          this.pendingUsers = this.pendingUsers.filter((u) => u.id !== user.id);
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
      .patch(`/admin/users/${user.id}/role`, { roleName: newRole })
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

  toggleCreateForm(): void {
    this.showCreateForm = !this.showCreateForm;
    if (!this.showCreateForm) {
      this.createForm.reset({ role: 'STUDENT' });
    }
  }

  createUser(): void {
    if (this.createForm.invalid) return;
    this.creatingUser = true;
    const { username, password, role } = this.createForm.value;
    this.apiService.post<UserRecord>('/admin/users', { username, password, role }).subscribe({
      next: (created) => {
        this.creatingUser = false;
        this.showCreateForm = false;
        this.createForm.reset({ role: 'STUDENT' });
        this.snackBar.open(`User ${created.username} created.`, 'Dismiss', { duration: 3000 });
        this.loadAllUsers();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.creatingUser = false;
        const msg = err?.status === 409 ? 'Username already exists.' : 'Failed to create user.';
        this.snackBar.open(msg, 'Dismiss', { duration: 3000 });
        this.cdr.markForCheck();
      },
    });
  }

  changeStatus(user: UserRecord, newStatus: string): void {
    if (user.status === newStatus) return;
    this.apiService.patch(`/admin/users/${user.id}/status`, { status: newStatus }).subscribe({
      next: () => {
        user.status = newStatus;
        this.snackBar.open(`Status updated to ${newStatus}.`, 'Dismiss', { duration: 3000 });
        this.cdr.markForCheck();
      },
      error: () => {
        this.snackBar.open('Failed to update status.', 'Dismiss', { duration: 3000 });
        this.cdr.markForCheck();
      },
    });
  }

  deleteUser(user: UserRecord): void {
    if (!window.confirm(`Permanently delete user "${user.username}"? This cannot be undone.`)) {
      return;
    }
    this.deletingUserId = user.id;
    this.apiService.delete(`/admin/users/${user.id}`).subscribe({
      next: () => {
        this.deletingUserId = null;
        this.users = this.users.filter((u) => u.id !== user.id);
        this.updatePagedUsers();
        this.snackBar.open(`User ${user.username} deleted.`, 'Dismiss', { duration: 3000 });
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.deletingUserId = null;
        const msg =
          err?.status === 400
            ? 'You cannot delete your own account.'
            : 'Failed to delete user.';
        this.snackBar.open(msg, 'Dismiss', { duration: 3000 });
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
    this.apiService.get<UserRecord[] | { content: UserRecord[] }>('/admin/users').subscribe({
      next: (d) => {
        // GET /admin/users returns a Spring Page ({ content: [...] }); unwrap it. Accept a plain
        // array too so unit tests can stub either shape.
        this.users = Array.isArray(d) ? d : d.content ?? [];
        this.loadingUsers = false;
        this.updatePagedUsers();
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingUsers = false;
        this.errorMessage = 'Failed to load users.';
        this.cdr.markForCheck();
      },
    });
  }
}
