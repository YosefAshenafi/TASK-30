import {
  Component,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { Subscription } from 'rxjs';
import { ApiService } from '../core/api.service';
import { environment } from '../../environments/environment';

interface EnrollmentRecord {
  enrollmentId: string;
  studentName: string;
  courseName: string;
  enrolledAt: string;
  status: string;
}

interface SeatUtilizationRecord {
  courseId: string;
  courseName: string;
  capacity: number;
  enrolled: number;
  utilizationPct: number;
}

interface RefundRecord {
  refundId: string;
  studentName: string;
  amount: number;
  reason: string;
  processedAt: string;
}

interface InventoryRecord {
  itemId: string;
  itemName: string;
  quantity: number;
  location: string;
  lastUpdated: string;
}

interface CertificationRecord {
  certId: string;
  studentName: string;
  courseName: string;
  issuedAt: string;
  expiresAt: string;
}

interface Notification {
  id: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
}

@Component({
  selector: 'app-schedule-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDialogModule,
  ],
  template: `
    <h2 mat-dialog-title>Schedule Report</h2>
    <mat-dialog-content>
      <form [formGroup]="scheduleForm" class="schedule-form">
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Report Type</mat-label>
          <mat-select formControlName="reportType">
            <mat-option value="ENROLLMENTS">Enrollments</mat-option>
            <mat-option value="SEAT_UTILIZATION">Seat Utilization</mat-option>
            <mat-option value="REFUNDS">Refunds</mat-option>
            <mat-option value="INVENTORY">Inventory</mat-option>
            <mat-option value="CERTIFICATIONS">Certifications</mat-option>
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Cron Expression</mat-label>
          <input matInput formControlName="cronExpression" placeholder="e.g. 0 9 * * MON" />
          <mat-hint>Standard 5-field cron: minute hour day month weekday</mat-hint>
          <mat-error *ngIf="scheduleForm.get('cronExpression')?.hasError('required')">
            Cron expression is required
          </mat-error>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Recipient Email</mat-label>
          <input matInput formControlName="email" placeholder="user@example.com" />
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button
        mat-raised-button
        color="primary"
        [mat-dialog-close]="scheduleForm.value"
        [disabled]="scheduleForm.invalid"
      >
        Schedule
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `
      .schedule-form { display: flex; flex-direction: column; gap: 8px; min-width: 360px; padding-top: 8px; }
      .full-width { width: 100%; }
    `,
  ],
})
export class ScheduleDialogComponent {
  scheduleForm: FormGroup;

  constructor(private fb: FormBuilder) {
    this.scheduleForm = this.fb.group({
      reportType: ['ENROLLMENTS', Validators.required],
      cronExpression: ['0 9 * * MON', Validators.required],
      email: [''],
    });
  }
}

@Component({
  selector: 'app-reports-center',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    MatTabsModule,
    MatTableModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatBadgeModule,
    MatDialogModule,
    MatSnackBarModule,
    MatDividerModule,
  ],
  template: `
    <div class="reports-container">
      <div class="page-header">
        <h1 class="page-title">Reports Center</h1>
        <button
          mat-icon-button
          [matBadge]="unreadCount > 0 ? unreadCount.toString() : null"
          matBadgeColor="warn"
          aria-label="Notifications"
          (click)="toggleNotifications()"
        >
          <mat-icon>notifications</mat-icon>
        </button>
      </div>

      <!-- Notifications Panel -->
      <mat-card class="notifications-panel" *ngIf="showNotifications">
        <mat-card-header>
          <mat-card-title>Notifications</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngFor="let n of notifications" class="notification-item" [class.unread]="!n.read">
            <strong>{{ n.title }}</strong>
            <p>{{ n.message }}</p>
            <small>{{ n.createdAt | date: 'short' }}</small>
          </div>
          <p *ngIf="notifications.length === 0" class="empty-note">No notifications.</p>
        </mat-card-content>
      </mat-card>

      <!-- Tabs -->
      <mat-tab-group animationDuration="200ms" (selectedTabChange)="onTabChange($event)">

        <!-- Enrollments -->
        <mat-tab label="Enrollments">
          <div class="tab-content">
            <ng-container *ngTemplateOutlet="filterBar; context: { $implicit: 'ENROLLMENTS' }"></ng-container>
            <div *ngIf="loadingEnrollments" class="loading-center"><mat-spinner diameter="40"></mat-spinner></div>
            <div class="empty-state" *ngIf="!loadingEnrollments && enrollments.length === 0">
              <mat-icon>person_add</mat-icon><p>No enrollment records found.</p>
            </div>
            <table mat-table [dataSource]="enrollments" class="full-width-table" *ngIf="!loadingEnrollments && enrollments.length > 0">
              <ng-container matColumnDef="studentName">
                <th mat-header-cell *matHeaderCellDef>Student</th>
                <td mat-cell *matCellDef="let r">{{ r.studentName }}</td>
              </ng-container>
              <ng-container matColumnDef="courseName">
                <th mat-header-cell *matHeaderCellDef>Course</th>
                <td mat-cell *matCellDef="let r">{{ r.courseName }}</td>
              </ng-container>
              <ng-container matColumnDef="enrolledAt">
                <th mat-header-cell *matHeaderCellDef>Enrolled At</th>
                <td mat-cell *matCellDef="let r">{{ r.enrolledAt | date: 'mediumDate' }}</td>
              </ng-container>
              <ng-container matColumnDef="status">
                <th mat-header-cell *matHeaderCellDef>Status</th>
                <td mat-cell *matCellDef="let r">{{ r.status }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['studentName','courseName','enrolledAt','status']"></tr>
              <tr mat-row *matRowDef="let r; columns: ['studentName','courseName','enrolledAt','status']"></tr>
            </table>
            <div class="export-row" *ngIf="!loadingEnrollments">
              <button mat-stroked-button (click)="exportReport('ENROLLMENTS', 'CSV')"><mat-icon>download</mat-icon>CSV</button>
              <button mat-stroked-button (click)="exportReport('ENROLLMENTS', 'PDF')"><mat-icon>picture_as_pdf</mat-icon>PDF</button>
              <button mat-raised-button color="accent" (click)="openScheduleDialog('ENROLLMENTS')"><mat-icon>schedule</mat-icon>Schedule</button>
            </div>
          </div>
        </mat-tab>

        <!-- Seat Utilization -->
        <mat-tab label="Seat Utilization">
          <div class="tab-content">
            <ng-container *ngTemplateOutlet="filterBar; context: { $implicit: 'SEAT_UTILIZATION' }"></ng-container>
            <div *ngIf="loadingSeats" class="loading-center"><mat-spinner diameter="40"></mat-spinner></div>
            <div class="empty-state" *ngIf="!loadingSeats && seatUtilization.length === 0">
              <mat-icon>event_seat</mat-icon><p>No seat utilization data found.</p>
            </div>
            <table mat-table [dataSource]="seatUtilization" class="full-width-table" *ngIf="!loadingSeats && seatUtilization.length > 0">
              <ng-container matColumnDef="courseName">
                <th mat-header-cell *matHeaderCellDef>Course</th>
                <td mat-cell *matCellDef="let r">{{ r.courseName }}</td>
              </ng-container>
              <ng-container matColumnDef="capacity">
                <th mat-header-cell *matHeaderCellDef>Capacity</th>
                <td mat-cell *matCellDef="let r">{{ r.capacity }}</td>
              </ng-container>
              <ng-container matColumnDef="enrolled">
                <th mat-header-cell *matHeaderCellDef>Enrolled</th>
                <td mat-cell *matCellDef="let r">{{ r.enrolled }}</td>
              </ng-container>
              <ng-container matColumnDef="utilizationPct">
                <th mat-header-cell *matHeaderCellDef>Utilization %</th>
                <td mat-cell *matCellDef="let r">{{ r.utilizationPct }}%</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['courseName','capacity','enrolled','utilizationPct']"></tr>
              <tr mat-row *matRowDef="let r; columns: ['courseName','capacity','enrolled','utilizationPct']"></tr>
            </table>
            <div class="export-row" *ngIf="!loadingSeats">
              <button mat-stroked-button (click)="exportReport('SEAT_UTILIZATION', 'CSV')"><mat-icon>download</mat-icon>CSV</button>
              <button mat-stroked-button (click)="exportReport('SEAT_UTILIZATION', 'PDF')"><mat-icon>picture_as_pdf</mat-icon>PDF</button>
              <button mat-raised-button color="accent" (click)="openScheduleDialog('SEAT_UTILIZATION')"><mat-icon>schedule</mat-icon>Schedule</button>
            </div>
          </div>
        </mat-tab>

        <!-- Refunds -->
        <mat-tab label="Refunds">
          <div class="tab-content">
            <ng-container *ngTemplateOutlet="filterBar; context: { $implicit: 'REFUNDS' }"></ng-container>
            <div *ngIf="loadingRefunds" class="loading-center"><mat-spinner diameter="40"></mat-spinner></div>
            <div class="empty-state" *ngIf="!loadingRefunds && refunds.length === 0">
              <mat-icon>money_off</mat-icon><p>No refund records found.</p>
            </div>
            <table mat-table [dataSource]="refunds" class="full-width-table" *ngIf="!loadingRefunds && refunds.length > 0">
              <ng-container matColumnDef="studentName">
                <th mat-header-cell *matHeaderCellDef>Student</th>
                <td mat-cell *matCellDef="let r">{{ r.studentName }}</td>
              </ng-container>
              <ng-container matColumnDef="amount">
                <th mat-header-cell *matHeaderCellDef>Amount</th>
                <td mat-cell *matCellDef="let r">{{ r.amount | currency }}</td>
              </ng-container>
              <ng-container matColumnDef="reason">
                <th mat-header-cell *matHeaderCellDef>Reason</th>
                <td mat-cell *matCellDef="let r">{{ r.reason }}</td>
              </ng-container>
              <ng-container matColumnDef="processedAt">
                <th mat-header-cell *matHeaderCellDef>Processed</th>
                <td mat-cell *matCellDef="let r">{{ r.processedAt | date: 'mediumDate' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['studentName','amount','reason','processedAt']"></tr>
              <tr mat-row *matRowDef="let r; columns: ['studentName','amount','reason','processedAt']"></tr>
            </table>
            <div class="export-row" *ngIf="!loadingRefunds">
              <button mat-stroked-button (click)="exportReport('REFUNDS', 'CSV')"><mat-icon>download</mat-icon>CSV</button>
              <button mat-stroked-button (click)="exportReport('REFUNDS', 'PDF')"><mat-icon>picture_as_pdf</mat-icon>PDF</button>
              <button mat-raised-button color="accent" (click)="openScheduleDialog('REFUNDS')"><mat-icon>schedule</mat-icon>Schedule</button>
            </div>
          </div>
        </mat-tab>

        <!-- Inventory -->
        <mat-tab label="Inventory">
          <div class="tab-content">
            <ng-container *ngTemplateOutlet="filterBar; context: { $implicit: 'INVENTORY' }"></ng-container>
            <div *ngIf="loadingInventory" class="loading-center"><mat-spinner diameter="40"></mat-spinner></div>
            <div class="empty-state" *ngIf="!loadingInventory && inventory.length === 0">
              <mat-icon>inventory_2</mat-icon><p>No inventory records found.</p>
            </div>
            <table mat-table [dataSource]="inventory" class="full-width-table" *ngIf="!loadingInventory && inventory.length > 0">
              <ng-container matColumnDef="itemName">
                <th mat-header-cell *matHeaderCellDef>Item</th>
                <td mat-cell *matCellDef="let r">{{ r.itemName }}</td>
              </ng-container>
              <ng-container matColumnDef="quantity">
                <th mat-header-cell *matHeaderCellDef>Quantity</th>
                <td mat-cell *matCellDef="let r">{{ r.quantity }}</td>
              </ng-container>
              <ng-container matColumnDef="location">
                <th mat-header-cell *matHeaderCellDef>Location</th>
                <td mat-cell *matCellDef="let r">{{ r.location }}</td>
              </ng-container>
              <ng-container matColumnDef="lastUpdated">
                <th mat-header-cell *matHeaderCellDef>Last Updated</th>
                <td mat-cell *matCellDef="let r">{{ r.lastUpdated | date: 'mediumDate' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['itemName','quantity','location','lastUpdated']"></tr>
              <tr mat-row *matRowDef="let r; columns: ['itemName','quantity','location','lastUpdated']"></tr>
            </table>
            <div class="export-row" *ngIf="!loadingInventory">
              <button mat-stroked-button (click)="exportReport('INVENTORY', 'CSV')"><mat-icon>download</mat-icon>CSV</button>
              <button mat-stroked-button (click)="exportReport('INVENTORY', 'PDF')"><mat-icon>picture_as_pdf</mat-icon>PDF</button>
              <button mat-raised-button color="accent" (click)="openScheduleDialog('INVENTORY')"><mat-icon>schedule</mat-icon>Schedule</button>
            </div>
          </div>
        </mat-tab>

        <!-- Certifications -->
        <mat-tab label="Certifications">
          <div class="tab-content">
            <ng-container *ngTemplateOutlet="filterBar; context: { $implicit: 'CERTIFICATIONS' }"></ng-container>
            <div *ngIf="loadingCerts" class="loading-center"><mat-spinner diameter="40"></mat-spinner></div>
            <div class="empty-state" *ngIf="!loadingCerts && certifications.length === 0">
              <mat-icon>workspace_premium</mat-icon><p>No certification records found.</p>
            </div>
            <table mat-table [dataSource]="certifications" class="full-width-table" *ngIf="!loadingCerts && certifications.length > 0">
              <ng-container matColumnDef="studentName">
                <th mat-header-cell *matHeaderCellDef>Student</th>
                <td mat-cell *matCellDef="let r">{{ r.studentName }}</td>
              </ng-container>
              <ng-container matColumnDef="courseName">
                <th mat-header-cell *matHeaderCellDef>Course</th>
                <td mat-cell *matCellDef="let r">{{ r.courseName }}</td>
              </ng-container>
              <ng-container matColumnDef="issuedAt">
                <th mat-header-cell *matHeaderCellDef>Issued</th>
                <td mat-cell *matCellDef="let r">{{ r.issuedAt | date: 'mediumDate' }}</td>
              </ng-container>
              <ng-container matColumnDef="expiresAt">
                <th mat-header-cell *matHeaderCellDef>Expires</th>
                <td mat-cell *matCellDef="let r">{{ r.expiresAt | date: 'mediumDate' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['studentName','courseName','issuedAt','expiresAt']"></tr>
              <tr mat-row *matRowDef="let r; columns: ['studentName','courseName','issuedAt','expiresAt']"></tr>
            </table>
            <div class="export-row" *ngIf="!loadingCerts">
              <button mat-stroked-button (click)="exportReport('CERTIFICATIONS', 'CSV')"><mat-icon>download</mat-icon>CSV</button>
              <button mat-stroked-button (click)="exportReport('CERTIFICATIONS', 'PDF')"><mat-icon>picture_as_pdf</mat-icon>PDF</button>
              <button mat-raised-button color="accent" (click)="openScheduleDialog('CERTIFICATIONS')"><mat-icon>schedule</mat-icon>Schedule</button>
            </div>
          </div>
        </mat-tab>

      </mat-tab-group>

      <!-- Shared Filter Bar Template -->
      <ng-template #filterBar let-reportType>
        <div class="filter-row">
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Date From</mat-label>
            <input matInput [matDatepicker]="dp1" [(ngModel)]="filterDateFrom" />
            <mat-datepicker-toggle matIconSuffix [for]="dp1"></mat-datepicker-toggle>
            <mat-datepicker #dp1></mat-datepicker>
          </mat-form-field>
          <mat-form-field appearance="outline" class="filter-field">
            <mat-label>Date To</mat-label>
            <input matInput [matDatepicker]="dp2" [(ngModel)]="filterDateTo" />
            <mat-datepicker-toggle matIconSuffix [for]="dp2"></mat-datepicker-toggle>
            <mat-datepicker #dp2></mat-datepicker>
          </mat-form-field>
          <button mat-stroked-button (click)="loadTabData(activeTabIndex)">
            <mat-icon>refresh</mat-icon>Refresh
          </button>
        </div>
      </ng-template>

      <div class="error-banner" *ngIf="errorMessage" role="alert">{{ errorMessage }}</div>
    </div>
  `,
  styles: [
    `
      .reports-container { max-width: 1100px; margin: 0 auto; }
      .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
      .page-title { font-size: 28px; font-weight: 500; margin: 0; }
      .tab-content { padding: 16px 0; }
      .loading-center { display: flex; justify-content: center; padding: 48px; }
      .full-width-table { width: 100%; }
      .export-row { display: flex; gap: 12px; margin-top: 16px; flex-wrap: wrap; align-items: center; }
      .filter-row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-bottom: 16px; }
      .filter-field { min-width: 160px; }
      .empty-state { text-align: center; padding: 48px; color: rgba(0,0,0,0.4); }
      .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; }
      .error-banner { background: #f44336; color: white; padding: 8px 16px; border-radius: 4px; margin-top: 12px; }
      .notifications-panel { margin-bottom: 16px; max-height: 320px; overflow-y: auto; }
      .notification-item { padding: 8px 0; border-bottom: 1px solid #e0e0e0; }
      .notification-item.unread { background: #e3f2fd; padding: 8px; border-radius: 4px; }
      .empty-note { color: rgba(0,0,0,0.4); text-align: center; }
    `,
  ],
})
export class ReportsCenterComponent implements OnInit, OnDestroy {
  enrollments: EnrollmentRecord[] = [];
  seatUtilization: SeatUtilizationRecord[] = [];
  refunds: RefundRecord[] = [];
  inventory: InventoryRecord[] = [];
  certifications: CertificationRecord[] = [];
  notifications: Notification[] = [];

  loadingEnrollments = false;
  loadingSeats = false;
  loadingRefunds = false;
  loadingInventory = false;
  loadingCerts = false;

  unreadCount = 0;
  showNotifications = false;
  errorMessage = '';
  activeTabIndex = 0;

  filterDateFrom: Date | null = null;
  filterDateTo: Date | null = null;

  private sseSubscription: EventSource | null = null;

  constructor(
    private apiService: ApiService,
    private dialog: MatDialog,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.loadEnrollments();
    this.loadNotifications();
    this.subscribeToSSE();
  }

  ngOnDestroy(): void {
    this.sseSubscription?.close();
  }

  onTabChange(event: { index: number }): void {
    this.activeTabIndex = event.index;
    this.loadTabData(event.index);
  }

  loadTabData(index: number): void {
    switch (index) {
      case 0: this.loadEnrollments(); break;
      case 1: this.loadSeatUtilization(); break;
      case 2: this.loadRefunds(); break;
      case 3: this.loadInventory(); break;
      case 4: this.loadCertifications(); break;
    }
  }

  toggleNotifications(): void {
    this.showNotifications = !this.showNotifications;
    if (this.showNotifications) {
      this.notifications.forEach((n) => (n.read = true));
      this.unreadCount = 0;
      this.cdr.markForCheck();
    }
  }

  exportReport(reportType: string, format: 'CSV' | 'PDF'): void {
    this.apiService
      .post<{ downloadPath: string }>('/reports/export', { reportType, format })
      .subscribe({
        next: (res) => {
          this.snackBar.open(`Export ready: ${res.downloadPath}`, 'Dismiss', {
            duration: 5000,
          });
          this.cdr.markForCheck();
        },
        error: () => {
          this.snackBar.open('Export failed. Please try again.', 'Dismiss', {
            duration: 4000,
          });
        },
      });
  }

  openScheduleDialog(reportType: string): void {
    const ref = this.dialog.open(ScheduleDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe((result) => {
      if (result) {
        this.apiService
          .post('/reports/schedule', { ...result, reportType })
          .subscribe({
            next: () => {
              this.snackBar.open('Report scheduled successfully.', 'Dismiss', {
                duration: 3000,
              });
            },
            error: () => {
              this.snackBar.open('Failed to schedule report.', 'Dismiss', {
                duration: 3000,
              });
            },
          });
      }
    });
  }

  private loadNotifications(): void {
    this.apiService.get<Notification[]>('/notifications').subscribe({
      next: (data) => {
        this.notifications = data;
        this.unreadCount = data.filter((n) => !n.read).length;
        this.cdr.markForCheck();
      },
    });
  }

  private subscribeToSSE(): void {
    const token = localStorage.getItem('meridian_token') ?? '';
    this.sseSubscription = new EventSource(
      `${environment.apiUrl}/api/notifications/stream`
    );
    this.sseSubscription.onmessage = (event) => {
      try {
        const notification: Notification = JSON.parse(event.data);
        this.notifications.unshift(notification);
        if (!notification.read) {
          this.unreadCount++;
        }
        this.cdr.markForCheck();
      } catch {
        // ignore malformed SSE messages
      }
    };
    this.sseSubscription.onerror = () => {
      this.sseSubscription?.close();
    };
  }

  private buildParams(): Record<string, string> {
    const params: Record<string, string> = {};
    if (this.filterDateFrom)
      params['dateFrom'] = this.filterDateFrom.toISOString().split('T')[0];
    if (this.filterDateTo)
      params['dateTo'] = this.filterDateTo.toISOString().split('T')[0];
    return params;
  }

  private loadEnrollments(): void {
    this.loadingEnrollments = true;
    this.apiService.get<EnrollmentRecord[]>('/reports/enrollments', this.buildParams()).subscribe({
      next: (d) => { this.enrollments = d; this.loadingEnrollments = false; this.cdr.markForCheck(); },
      error: () => { this.loadingEnrollments = false; this.errorMessage = 'Failed to load enrollments.'; this.cdr.markForCheck(); },
    });
  }

  private loadSeatUtilization(): void {
    this.loadingSeats = true;
    this.apiService.get<SeatUtilizationRecord[]>('/reports/seat-utilization', this.buildParams()).subscribe({
      next: (d) => { this.seatUtilization = d; this.loadingSeats = false; this.cdr.markForCheck(); },
      error: () => { this.loadingSeats = false; this.errorMessage = 'Failed to load seat utilization.'; this.cdr.markForCheck(); },
    });
  }

  private loadRefunds(): void {
    this.loadingRefunds = true;
    this.apiService.get<RefundRecord[]>('/reports/refunds', this.buildParams()).subscribe({
      next: (d) => { this.refunds = d; this.loadingRefunds = false; this.cdr.markForCheck(); },
      error: () => { this.loadingRefunds = false; this.errorMessage = 'Failed to load refunds.'; this.cdr.markForCheck(); },
    });
  }

  private loadInventory(): void {
    this.loadingInventory = true;
    this.apiService.get<InventoryRecord[]>('/reports/inventory', this.buildParams()).subscribe({
      next: (d) => { this.inventory = d; this.loadingInventory = false; this.cdr.markForCheck(); },
      error: () => { this.loadingInventory = false; this.errorMessage = 'Failed to load inventory.'; this.cdr.markForCheck(); },
    });
  }

  private loadCertifications(): void {
    this.loadingCerts = true;
    this.apiService.get<CertificationRecord[]>('/reports/certifications', this.buildParams()).subscribe({
      next: (d) => { this.certifications = d; this.loadingCerts = false; this.cdr.markForCheck(); },
      error: () => { this.loadingCerts = false; this.errorMessage = 'Failed to load certifications.'; this.cdr.markForCheck(); },
    });
  }
}
