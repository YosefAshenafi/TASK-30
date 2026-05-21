import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatRadioModule } from '@angular/material/radio';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../core/api.service';

interface BackupRecord {
  backupId: string;
  type: 'FULL' | 'INCREMENTAL';
  createdAt: string;
  sizeBytes: number;
  retentionUntil: string;
  status: string;
}

interface RecycleBinRecord {
  recycleId: string;
  entityType: string;
  entityId: string;
  deletedAt: string;
  expiresAt: string;
  deletedBy: string;
}

@Component({
  selector: 'app-backup-admin',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatRadioModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatSnackBarModule,
    MatDividerModule,
  ],
  template: `
    <div class="backup-container">
      <h1 class="page-title">Backup &amp; Disaster Recovery</h1>

      <!-- Section 1: Trigger Backup -->
      <mat-card class="section-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>cloud_upload</mat-icon>
          <mat-card-title>Trigger Backup</mat-card-title>
          <mat-card-subtitle>Initiate a manual backup</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="backupForm">
            <mat-radio-group formControlName="backupType" class="backup-type-group">
              <mat-radio-button value="FULL" color="primary">Full Backup</mat-radio-button>
              <mat-radio-button value="INCREMENTAL" color="primary">Incremental Backup</mat-radio-button>
            </mat-radio-group>
          </form>
        </mat-card-content>
        <mat-card-actions>
          <button
            mat-raised-button
            color="primary"
            (click)="triggerBackup()"
            [disabled]="triggeringBackup"
          >
            <mat-spinner diameter="20" *ngIf="triggeringBackup"></mat-spinner>
            <mat-icon *ngIf="!triggeringBackup">backup</mat-icon>
            {{ triggeringBackup ? 'Triggering...' : 'Trigger Backup' }}
          </button>
        </mat-card-actions>
      </mat-card>

      <mat-divider class="section-divider"></mat-divider>

      <!-- Section 2: Backup History -->
      <mat-card class="section-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>history</mat-icon>
          <mat-card-title>Backup History</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loadingHistory" class="loading-center">
            <mat-spinner diameter="40"></mat-spinner>
          </div>
          <div class="empty-state" *ngIf="!loadingHistory && backupHistory.length === 0">
            <mat-icon>inbox</mat-icon>
            <p>No backup records found.</p>
          </div>
          <table
            mat-table
            [dataSource]="backupHistory"
            class="full-width-table"
            *ngIf="!loadingHistory && backupHistory.length > 0"
          >
            <ng-container matColumnDef="type">
              <th mat-header-cell *matHeaderCellDef>Type</th>
              <td mat-cell *matCellDef="let r">{{ r.type }}</td>
            </ng-container>
            <ng-container matColumnDef="createdAt">
              <th mat-header-cell *matHeaderCellDef>Created</th>
              <td mat-cell *matCellDef="let r">{{ r.createdAt | date: 'medium' }}</td>
            </ng-container>
            <ng-container matColumnDef="sizeBytes">
              <th mat-header-cell *matHeaderCellDef>Size</th>
              <td mat-cell *matCellDef="let r">{{ formatBytes(r.sizeBytes) }}</td>
            </ng-container>
            <ng-container matColumnDef="retentionUntil">
              <th mat-header-cell *matHeaderCellDef>Retention Until</th>
              <td mat-cell *matCellDef="let r">{{ r.retentionUntil | date: 'mediumDate' }}</td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let r">{{ r.status }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="historyColumns"></tr>
            <tr mat-row *matRowDef="let r; columns: historyColumns"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <mat-divider class="section-divider"></mat-divider>

      <!-- Section 3: Recycle Bin -->
      <mat-card class="section-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>delete_outline</mat-icon>
          <mat-card-title>Recycle Bin</mat-card-title>
          <mat-card-subtitle>Soft-deleted entities pending permanent removal</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loadingRecycleBin" class="loading-center">
            <mat-spinner diameter="40"></mat-spinner>
          </div>
          <div class="empty-state" *ngIf="!loadingRecycleBin && recycleBin.length === 0">
            <mat-icon>delete_sweep</mat-icon>
            <p>Recycle bin is empty.</p>
          </div>
          <table
            mat-table
            [dataSource]="recycleBin"
            class="full-width-table"
            *ngIf="!loadingRecycleBin && recycleBin.length > 0"
          >
            <ng-container matColumnDef="entityType">
              <th mat-header-cell *matHeaderCellDef>Entity Type</th>
              <td mat-cell *matCellDef="let r">{{ r.entityType }}</td>
            </ng-container>
            <ng-container matColumnDef="entityId">
              <th mat-header-cell *matHeaderCellDef>Entity ID</th>
              <td mat-cell *matCellDef="let r">
                <code>{{ r.entityId }}</code>
              </td>
            </ng-container>
            <ng-container matColumnDef="deletedAt">
              <th mat-header-cell *matHeaderCellDef>Deleted At</th>
              <td mat-cell *matCellDef="let r">{{ r.deletedAt | date: 'medium' }}</td>
            </ng-container>
            <ng-container matColumnDef="expiresAt">
              <th mat-header-cell *matHeaderCellDef>Expires At</th>
              <td mat-cell *matCellDef="let r">{{ r.expiresAt | date: 'mediumDate' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let r">
                <button
                  mat-stroked-button
                  color="primary"
                  (click)="restoreEntity(r)"
                  [disabled]="processingId === r.recycleId"
                  class="action-btn"
                >
                  <mat-icon>restore</mat-icon>
                  Restore
                </button>
                <button
                  mat-stroked-button
                  color="warn"
                  (click)="purgeEntity(r)"
                  [disabled]="processingId === r.recycleId"
                >
                  <mat-icon>delete_forever</mat-icon>
                  Delete
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="recycleColumns"></tr>
            <tr mat-row *matRowDef="let r; columns: recycleColumns"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <div class="error-banner" *ngIf="errorMessage" role="alert">{{ errorMessage }}</div>
    </div>
  `,
  styles: [
    `
      .backup-container { max-width: 960px; margin: 0 auto; }
      .page-title { font-size: 28px; font-weight: 500; margin-bottom: 24px; }
      .section-card { margin-bottom: 16px; }
      .section-divider { margin: 16px 0; }
      .backup-type-group { display: flex; gap: 24px; padding: 8px 0; }
      .loading-center { display: flex; justify-content: center; padding: 48px; }
      .full-width-table { width: 100%; }
      .action-btn { margin-right: 8px; }
      .empty-state { text-align: center; padding: 48px; color: rgba(0,0,0,0.4); }
      .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; }
      .error-banner { background: #f44336; color: white; padding: 8px 16px; border-radius: 4px; margin-top: 12px; }
      code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-size: 12px; }
    `,
  ],
})
export class BackupAdminComponent implements OnInit {
  backupForm: FormGroup;
  backupHistory: BackupRecord[] = [];
  recycleBin: RecycleBinRecord[] = [];

  historyColumns = ['type', 'createdAt', 'sizeBytes', 'retentionUntil', 'status'];
  recycleColumns = ['entityType', 'entityId', 'deletedAt', 'expiresAt', 'actions'];

  loadingHistory = false;
  loadingRecycleBin = false;
  triggeringBackup = false;
  processingId: string | null = null;
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {
    this.backupForm = this.fb.group({
      backupType: ['FULL'],
    });
  }

  ngOnInit(): void {
    this.loadBackupHistory();
    this.loadRecycleBin();
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  }

  triggerBackup(): void {
    this.triggeringBackup = true;
    const backupType = this.backupForm.get('backupType')!.value;
    this.apiService
      .post<BackupRecord>('/admin/backup/trigger', { type: backupType })
      .subscribe({
        next: (newBackup) => {
          this.triggeringBackup = false;
          this.backupHistory.unshift(newBackup);
          this.snackBar.open(`${backupType} backup triggered successfully.`, 'Dismiss', {
            duration: 4000,
          });
          this.cdr.markForCheck();
        },
        error: () => {
          this.triggeringBackup = false;
          this.snackBar.open('Failed to trigger backup.', 'Dismiss', { duration: 4000 });
          this.cdr.markForCheck();
        },
      });
  }

  restoreEntity(record: RecycleBinRecord): void {
    this.processingId = record.recycleId;
    this.apiService
      .post(`/admin/recycle-bin/${record.recycleId}/restore`, {})
      .subscribe({
        next: () => {
          this.recycleBin = this.recycleBin.filter((r) => r.recycleId !== record.recycleId);
          this.processingId = null;
          this.snackBar.open('Entity restored.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
        error: () => {
          this.processingId = null;
          this.snackBar.open('Failed to restore entity.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
      });
  }

  purgeEntity(record: RecycleBinRecord): void {
    this.processingId = record.recycleId;
    this.apiService
      .delete(`/admin/recycle-bin/${record.recycleId}`)
      .subscribe({
        next: () => {
          this.recycleBin = this.recycleBin.filter((r) => r.recycleId !== record.recycleId);
          this.processingId = null;
          this.snackBar.open('Entity permanently deleted.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
        error: () => {
          this.processingId = null;
          this.snackBar.open('Failed to delete entity.', 'Dismiss', { duration: 3000 });
          this.cdr.markForCheck();
        },
      });
  }

  private loadBackupHistory(): void {
    this.loadingHistory = true;
    this.apiService.get<BackupRecord[]>('/admin/backup/history').subscribe({
      next: (d) => {
        this.backupHistory = d;
        this.loadingHistory = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingHistory = false;
        this.errorMessage = 'Failed to load backup history.';
        this.cdr.markForCheck();
      },
    });
  }

  private loadRecycleBin(): void {
    this.loadingRecycleBin = true;
    this.apiService.get<RecycleBinRecord[]>('/admin/recycle-bin').subscribe({
      next: (d) => {
        this.recycleBin = d;
        this.loadingRecycleBin = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingRecycleBin = false;
        this.errorMessage = 'Failed to load recycle bin.';
        this.cdr.markForCheck();
      },
    });
  }
}
