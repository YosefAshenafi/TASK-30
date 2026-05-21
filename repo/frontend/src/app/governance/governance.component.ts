import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../core/api.service';

interface UserOption {
  userId: string;
  username: string;
}

interface DataPermission {
  permissionId: string;
  fieldName: string;
  classification: string;
  grantedBy: string;
  grantedAt: string;
}

const CLASSIFICATIONS = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED', 'PII'];

@Component({
  selector: 'app-governance',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatTableModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatSnackBarModule,
    MatDividerModule,
  ],
  template: `
    <div class="governance-container">
      <h1 class="page-title">Data Governance</h1>

      <!-- User Selector -->
      <mat-card class="section-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>manage_accounts</mat-icon>
          <mat-card-title>Select User</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loadingUsers" class="loading-center">
            <mat-spinner diameter="32"></mat-spinner>
          </div>
          <mat-form-field appearance="outline" class="user-select-field" *ngIf="!loadingUsers">
            <mat-label>User</mat-label>
            <mat-select [(value)]="selectedUserId" (selectionChange)="onUserSelect($event.value)">
              <mat-option *ngFor="let user of users" [value]="user.userId">
                {{ user.username }}
              </mat-option>
            </mat-select>
          </mat-form-field>
        </mat-card-content>
      </mat-card>

      <!-- Permissions Table -->
      <mat-card class="section-card" *ngIf="selectedUserId">
        <mat-card-header>
          <mat-icon mat-card-avatar>lock</mat-icon>
          <mat-card-title>Data Permissions</mat-card-title>
          <mat-card-subtitle>Current field-level access grants</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loadingPerms" class="loading-center">
            <mat-spinner diameter="40"></mat-spinner>
          </div>
          <div class="empty-state" *ngIf="!loadingPerms && permissions.length === 0">
            <mat-icon>no_encryption</mat-icon>
            <p>No data permissions granted to this user.</p>
          </div>
          <table
            mat-table
            [dataSource]="permissions"
            class="full-width-table"
            *ngIf="!loadingPerms && permissions.length > 0"
          >
            <ng-container matColumnDef="fieldName">
              <th mat-header-cell *matHeaderCellDef>Field Name</th>
              <td mat-cell *matCellDef="let r">
                <code>{{ r.fieldName }}</code>
              </td>
            </ng-container>
            <ng-container matColumnDef="classification">
              <th mat-header-cell *matHeaderCellDef>Classification</th>
              <td mat-cell *matCellDef="let r">
                <span class="classification-badge" [ngClass]="getClassBadge(r.classification)">
                  {{ r.classification }}
                </span>
              </td>
            </ng-container>
            <ng-container matColumnDef="grantedBy">
              <th mat-header-cell *matHeaderCellDef>Granted By</th>
              <td mat-cell *matCellDef="let r">{{ r.grantedBy }}</td>
            </ng-container>
            <ng-container matColumnDef="grantedAt">
              <th mat-header-cell *matHeaderCellDef>Granted At</th>
              <td mat-cell *matCellDef="let r">{{ r.grantedAt | date: 'medium' }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="permColumns"></tr>
            <tr mat-row *matRowDef="let r; columns: permColumns"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <mat-divider class="section-divider" *ngIf="selectedUserId"></mat-divider>

      <!-- Grant Permission Form -->
      <mat-card class="section-card" *ngIf="selectedUserId">
        <mat-card-header>
          <mat-icon mat-card-avatar>add_moderator</mat-icon>
          <mat-card-title>Grant Permission</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="grantForm" (ngSubmit)="grantPermission()" class="grant-form">
            <mat-form-field appearance="outline" class="grant-field">
              <mat-label>Field Name</mat-label>
              <input
                matInput
                formControlName="fieldName"
                placeholder="e.g. ssn, date_of_birth"
              />
              <mat-error *ngIf="grantForm.get('fieldName')?.hasError('required')">
                Field name is required
              </mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="grant-field">
              <mat-label>Classification</mat-label>
              <mat-select formControlName="classification">
                <mat-option *ngFor="let c of classifications" [value]="c">{{ c }}</mat-option>
              </mat-select>
              <mat-error *ngIf="grantForm.get('classification')?.hasError('required')">
                Classification is required
              </mat-error>
            </mat-form-field>

            <button
              mat-raised-button
              color="primary"
              type="submit"
              [disabled]="grantForm.invalid || grantingPerm"
            >
              <mat-spinner diameter="20" *ngIf="grantingPerm"></mat-spinner>
              <mat-icon *ngIf="!grantingPerm">add</mat-icon>
              {{ grantingPerm ? 'Granting...' : 'Grant Permission' }}
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <div class="error-banner" *ngIf="errorMessage" role="alert">{{ errorMessage }}</div>
    </div>
  `,
  styles: [
    `
      .governance-container { max-width: 960px; margin: 0 auto; }
      .page-title { font-size: 28px; font-weight: 500; margin-bottom: 24px; }
      .section-card { margin-bottom: 16px; }
      .section-divider { margin: 16px 0; }
      .user-select-field { min-width: 300px; }
      .loading-center { display: flex; justify-content: center; padding: 32px; }
      .full-width-table { width: 100%; }
      .grant-form { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; padding-top: 8px; }
      .grant-field { min-width: 200px; }
      .empty-state { text-align: center; padding: 48px; color: rgba(0,0,0,0.4); }
      .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; }
      .error-banner { background: #f44336; color: white; padding: 8px 16px; border-radius: 4px; margin-top: 12px; }
      code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-size: 12px; }
      .classification-badge {
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: 600;
        text-transform: uppercase;
      }
      .class-public { background: #e8f5e9; color: #2e7d32; }
      .class-internal { background: #e3f2fd; color: #1565c0; }
      .class-confidential { background: #fff3e0; color: #e65100; }
      .class-restricted { background: #fce4ec; color: #c62828; }
      .class-pii { background: #f3e5f5; color: #6a1b9a; }
    `,
  ],
})
export class GovernanceComponent implements OnInit {
  users: UserOption[] = [];
  permissions: DataPermission[] = [];
  classifications = CLASSIFICATIONS;
  permColumns = ['fieldName', 'classification', 'grantedBy', 'grantedAt'];

  selectedUserId: string | null = null;
  loadingUsers = false;
  loadingPerms = false;
  grantingPerm = false;
  errorMessage = '';

  grantForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {
    this.grantForm = this.fb.group({
      fieldName: ['', [Validators.required]],
      classification: ['CONFIDENTIAL', [Validators.required]],
    });
  }

  ngOnInit(): void {
    this.loadUsers();
  }

  onUserSelect(userId: string): void {
    this.selectedUserId = userId;
    this.loadPermissions(userId);
  }

  grantPermission(): void {
    if (this.grantForm.invalid || !this.selectedUserId) return;
    this.grantingPerm = true;

    const { fieldName, classification } = this.grantForm.value;
    this.apiService
      .post<DataPermission>(`/governance/users/${this.selectedUserId}/permissions`, {
        fieldName,
        classification,
      })
      .subscribe({
        next: (perm) => {
          this.permissions.push(perm);
          this.grantingPerm = false;
          this.grantForm.reset({ classification: 'CONFIDENTIAL' });
          this.snackBar.open('Permission granted.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
        error: () => {
          this.grantingPerm = false;
          this.snackBar.open('Failed to grant permission.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
      });
  }

  getClassBadge(classification: string): string {
    return `class-${classification.toLowerCase()}`;
  }

  private loadUsers(): void {
    this.loadingUsers = true;
    this.apiService.get<UserOption[]>('/admin/users').subscribe({
      next: (d) => {
        this.users = d.map((u: any) => ({ userId: u.userId, username: u.username }));
        this.loadingUsers = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingUsers = false;
        this.errorMessage = 'Failed to load users.';
        this.cdr.markForCheck();
      },
    });
  }

  private loadPermissions(userId: string): void {
    this.loadingPerms = true;
    this.apiService.get<DataPermission[]>(`/governance/users/${userId}/permissions`).subscribe({
      next: (d) => {
        this.permissions = d;
        this.loadingPerms = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingPerms = false;
        this.errorMessage = 'Failed to load permissions.';
        this.cdr.markForCheck();
      },
    });
  }
}
