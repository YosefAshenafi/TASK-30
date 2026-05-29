import {
  Component,
  OnInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
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
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';

interface MasteryTrend {
  week: string;
  masteryRate: number;
  totalAttempts: number;
}

interface WrongAnswer {
  itemId: string;
  question: string;
  knowledgePoint: string;
  wrongCount: number;
  wrongRate: number;
}

interface KnowledgeGap {
  knowledgePoint: string;
  wrongRate: number;
  totalAttempts: number;
}

interface ItemDifficulty {
  itemId: string;
  question: string;
  storedDifficulty: number;
  observedDifficulty: number;
  discrimination: number;
  totalAttempts: number;
}

@Component({
  selector: 'app-analytics-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    ReactiveFormsModule,
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
  ],
  template: `
    <div class="analytics-container">
      <h1 class="page-title">Analytics Dashboard</h1>

      <!-- Filter Bar -->
      <mat-card class="filter-card">
        <mat-card-content>
          <form [formGroup]="filterForm" class="filter-form" (ngSubmit)="applyFilters()">
            <mat-form-field appearance="outline" class="filter-field">
              <mat-label>Date From</mat-label>
              <input matInput [matDatepicker]="pickerFrom" formControlName="dateFrom" />
              <mat-datepicker-toggle matIconSuffix [for]="pickerFrom"></mat-datepicker-toggle>
              <mat-datepicker #pickerFrom></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline" class="filter-field">
              <mat-label>Date To</mat-label>
              <input matInput [matDatepicker]="pickerTo" formControlName="dateTo" />
              <mat-datepicker-toggle matIconSuffix [for]="pickerTo"></mat-datepicker-toggle>
              <mat-datepicker #pickerTo></mat-datepicker>
            </mat-form-field>

            <mat-form-field appearance="outline" class="filter-field">
              <mat-label>Location</mat-label>
              <mat-select formControlName="location">
                <mat-option value="">All Locations</mat-option>
                <mat-option value="site-a">Site A</mat-option>
                <mat-option value="site-b">Site B</mat-option>
                <mat-option value="remote">Remote</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="filter-field">
              <mat-label>Instructor</mat-label>
              <mat-select formControlName="instructor">
                <mat-option value="">All Instructors</mat-option>
              </mat-select>
            </mat-form-field>

            <mat-form-field appearance="outline" class="filter-field">
              <mat-label>Course Version</mat-label>
              <mat-select formControlName="courseVersion">
                <mat-option value="">All Versions</mat-option>
              </mat-select>
            </mat-form-field>

            <button mat-raised-button color="primary" type="submit">
              <mat-icon>filter_list</mat-icon>
              Apply Filters
            </button>
          </form>
        </mat-card-content>
      </mat-card>

      <!-- Tabs -->
      <mat-tab-group animationDuration="200ms" (selectedTabChange)="onTabChange($event)">

        <!-- Tab 1: Mastery Trends -->
        <mat-tab label="Mastery Trends">
          <div class="tab-content">
            <div *ngIf="loadingMastery" class="loading-center">
              <mat-spinner diameter="40"></mat-spinner>
            </div>
            <div class="empty-state" *ngIf="!loadingMastery && masteryTrends.length === 0">
              <mat-icon>bar_chart</mat-icon>
              <p>No mastery data for the selected filters.</p>
            </div>
            <ng-container *ngIf="!loadingMastery && masteryTrends.length > 0">
              <div class="mastery-chart">
                <div
                  class="mastery-bar-row"
                  *ngFor="let row of masteryTrends"
                >
                  <span class="bar-label">{{ row.week }}</span>
                  <div class="bar-track">
                    <div
                      class="bar-fill"
                      [style.width.%]="row.masteryRate"
                      [class.bar-high]="row.masteryRate >= 80"
                      [class.bar-medium]="row.masteryRate >= 60 && row.masteryRate < 80"
                      [class.bar-low]="row.masteryRate < 60"
                    ></div>
                  </div>
                  <span class="bar-pct">{{ row.masteryRate }}%</span>
                </div>
              </div>
              <table mat-table [dataSource]="masteryTrends" class="full-width-table mt-16">
                <ng-container matColumnDef="week">
                  <th mat-header-cell *matHeaderCellDef>Week</th>
                  <td mat-cell *matCellDef="let r">{{ r.week }}</td>
                </ng-container>
                <ng-container matColumnDef="masteryRate">
                  <th mat-header-cell *matHeaderCellDef>Mastery %</th>
                  <td mat-cell *matCellDef="let r">{{ r.masteryRate }}%</td>
                </ng-container>
                <ng-container matColumnDef="totalAttempts">
                  <th mat-header-cell *matHeaderCellDef>Attempts</th>
                  <td mat-cell *matCellDef="let r">{{ r.totalAttempts }}</td>
                </ng-container>
                <tr mat-header-row *matHeaderRowDef="['week','masteryRate','totalAttempts']"></tr>
                <tr mat-row *matRowDef="let r; columns: ['week','masteryRate','totalAttempts']"></tr>
              </table>
            </ng-container>
          </div>
        </mat-tab>

        <!-- Tab 2: Wrong Answers -->
        <mat-tab label="Wrong Answers">
          <div class="tab-content">
            <div *ngIf="loadingWrong" class="loading-center">
              <mat-spinner diameter="40"></mat-spinner>
            </div>
            <div class="empty-state" *ngIf="!loadingWrong && wrongAnswers.length === 0">
              <mat-icon>quiz</mat-icon>
              <p>No wrong answer data for the selected filters.</p>
            </div>
            <table
              mat-table
              [dataSource]="wrongAnswers"
              class="full-width-table"
              *ngIf="!loadingWrong && wrongAnswers.length > 0"
            >
              <ng-container matColumnDef="question">
                <th mat-header-cell *matHeaderCellDef>Item</th>
                <td mat-cell *matCellDef="let r">{{ r.question }}</td>
              </ng-container>
              <ng-container matColumnDef="knowledgePoint">
                <th mat-header-cell *matHeaderCellDef>Knowledge Point</th>
                <td mat-cell *matCellDef="let r">{{ r.knowledgePoint }}</td>
              </ng-container>
              <ng-container matColumnDef="wrongCount">
                <th mat-header-cell *matHeaderCellDef>Wrong Count</th>
                <td mat-cell *matCellDef="let r">{{ r.wrongCount }}</td>
              </ng-container>
              <ng-container matColumnDef="wrongRate">
                <th mat-header-cell *matHeaderCellDef>Wrong %</th>
                <td mat-cell *matCellDef="let r">{{ r.wrongRate }}%</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['question','knowledgePoint','wrongCount','wrongRate']"></tr>
              <tr mat-row *matRowDef="let r; columns: ['question','knowledgePoint','wrongCount','wrongRate']"></tr>
            </table>
          </div>
        </mat-tab>

        <!-- Tab 3: Knowledge Gaps -->
        <mat-tab label="Knowledge Gaps">
          <div class="tab-content">
            <div *ngIf="loadingGaps" class="loading-center">
              <mat-spinner diameter="40"></mat-spinner>
            </div>
            <div class="empty-state" *ngIf="!loadingGaps && knowledgeGaps.length === 0">
              <mat-icon>psychology</mat-icon>
              <p>No knowledge gap data for the selected filters.</p>
            </div>
            <table
              mat-table
              [dataSource]="knowledgeGaps"
              class="full-width-table"
              *ngIf="!loadingGaps && knowledgeGaps.length > 0"
            >
              <ng-container matColumnDef="knowledgePoint">
                <th mat-header-cell *matHeaderCellDef>Knowledge Point</th>
                <td mat-cell *matCellDef="let r">{{ r.knowledgePoint }}</td>
              </ng-container>
              <ng-container matColumnDef="wrongRate">
                <th mat-header-cell *matHeaderCellDef>Wrong %</th>
                <td mat-cell *matCellDef="let r">{{ r.wrongRate | number: '1.2-2' }}%</td>
              </ng-container>
              <ng-container matColumnDef="totalAttempts">
                <th mat-header-cell *matHeaderCellDef>Total Attempts</th>
                <td mat-cell *matCellDef="let r">{{ r.totalAttempts }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['knowledgePoint','wrongRate','totalAttempts']"></tr>
              <tr mat-row *matRowDef="let r; columns: ['knowledgePoint','wrongRate','totalAttempts']"></tr>
            </table>
          </div>
        </mat-tab>

        <!-- Tab 4: Item Difficulty -->
        <mat-tab label="Item Difficulty">
          <div class="tab-content">
            <div *ngIf="loadingDifficulty" class="loading-center">
              <mat-spinner diameter="40"></mat-spinner>
            </div>
            <div class="empty-state" *ngIf="!loadingDifficulty && itemDifficulty.length === 0">
              <mat-icon>leaderboard</mat-icon>
              <p>No item difficulty data for the selected filters.</p>
            </div>
            <table
              mat-table
              [dataSource]="itemDifficulty"
              class="full-width-table"
              *ngIf="!loadingDifficulty && itemDifficulty.length > 0"
            >
              <ng-container matColumnDef="question">
                <th mat-header-cell *matHeaderCellDef>Item</th>
                <td mat-cell *matCellDef="let r">{{ r.question }}</td>
              </ng-container>
              <ng-container matColumnDef="storedDifficulty">
                <th mat-header-cell *matHeaderCellDef>Stored Difficulty</th>
                <td mat-cell *matCellDef="let r">{{ r.storedDifficulty | number: '1.3-3' }}</td>
              </ng-container>
              <ng-container matColumnDef="observedDifficulty">
                <th mat-header-cell *matHeaderCellDef>Observed Difficulty</th>
                <td mat-cell *matCellDef="let r">{{ r.observedDifficulty | number: '1.3-3' }}</td>
              </ng-container>
              <ng-container matColumnDef="discrimination">
                <th mat-header-cell *matHeaderCellDef>Discrimination</th>
                <td mat-cell *matCellDef="let r">{{ r.discrimination | number: '1.3-3' }}</td>
              </ng-container>
              <tr mat-header-row *matHeaderRowDef="['question','storedDifficulty','observedDifficulty','discrimination']"></tr>
              <tr mat-row *matRowDef="let r; columns: ['question','storedDifficulty','observedDifficulty','discrimination']"></tr>
            </table>
          </div>
        </mat-tab>

      </mat-tab-group>

      <div class="error-banner" *ngIf="errorMessage" role="alert">
        {{ errorMessage }}
      </div>
    </div>
  `,
  styles: [
    `
      .analytics-container {
        max-width: 1100px;
        margin: 0 auto;
      }
      .page-title {
        font-size: 28px;
        font-weight: 500;
        margin-bottom: 24px;
      }
      .filter-card {
        margin-bottom: 24px;
      }
      .filter-form {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        align-items: flex-start;
        padding-top: 8px;
      }
      .filter-field {
        min-width: 160px;
      }
      .tab-content {
        padding: 16px 0;
      }
      .loading-center {
        display: flex;
        justify-content: center;
        padding: 48px;
      }
      .full-width-table {
        width: 100%;
      }
      .mt-16 {
        margin-top: 16px;
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
      .mastery-chart {
        margin-bottom: 8px;
      }
      .mastery-bar-row {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 8px;
      }
      .bar-label {
        width: 160px;
        font-size: 13px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .bar-track {
        flex: 1;
        height: 20px;
        background: #e0e0e0;
        border-radius: 4px;
        overflow: hidden;
      }
      .bar-fill {
        height: 100%;
        border-radius: 4px;
        transition: width 0.4s ease;
      }
      .bar-high {
        background: #4caf50;
      }
      .bar-medium {
        background: #ff9800;
      }
      .bar-low {
        background: #f44336;
      }
      .bar-pct {
        width: 48px;
        font-size: 13px;
        font-weight: 600;
        text-align: right;
      }
    `,
  ],
})
export class AnalyticsDashboardComponent implements OnInit {
  filterForm: FormGroup;

  masteryTrends: MasteryTrend[] = [];
  wrongAnswers: WrongAnswer[] = [];
  knowledgeGaps: KnowledgeGap[] = [];
  itemDifficulty: ItemDifficulty[] = [];

  loadingMastery = false;
  loadingWrong = false;
  loadingGaps = false;
  loadingDifficulty = false;

  errorMessage = '';

  private activeTabIndex = 0;

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private authService: AuthService,
    private cdr: ChangeDetectorRef
  ) {
    this.filterForm = this.fb.group({
      dateFrom: [null],
      dateTo: [null],
      location: [''],
      instructor: [''],
      courseVersion: [''],
    });
  }

  ngOnInit(): void {
    this.loadTab(0);
  }

  onTabChange(event: { index: number }): void {
    this.activeTabIndex = event.index;
    this.loadTab(event.index);
  }

  applyFilters(): void {
    this.loadTab(this.activeTabIndex);
  }

  private buildParams(): Record<string, string> {
    const params: Record<string, string> = {};
    const v = this.filterForm.value;
    if (v.dateFrom) params['dateFrom'] = (v.dateFrom as Date).toISOString().split('T')[0];
    if (v.dateTo) params['dateTo'] = (v.dateTo as Date).toISOString().split('T')[0];
    if (v.location) params['location'] = v.location;
    if (v.instructor) params['instructor'] = v.instructor;
    if (v.courseVersion) params['courseVersion'] = v.courseVersion;
    if (this.authService.hasRole('CORPORATE_MENTOR')) {
      const orgId = this.authService.getUserId();
      if (orgId) params['orgId'] = orgId;
    }
    return params;
  }

  private loadTab(index: number): void {
    switch (index) {
      case 0: this.loadMastery(); break;
      case 1: this.loadWrongAnswers(); break;
      case 2: this.loadKnowledgeGaps(); break;
      case 3: this.loadItemDifficulty(); break;
    }
  }

  private loadMastery(): void {
    this.loadingMastery = true;
    this.apiService.get<MasteryTrend[]>('/analytics/mastery', this.buildParams()).subscribe({
      next: (data) => {
        this.masteryTrends = data;
        this.loadingMastery = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingMastery = false;
        this.errorMessage = 'Failed to load mastery trends.';
        this.cdr.markForCheck();
      },
    });
  }

  private loadWrongAnswers(): void {
    this.loadingWrong = true;
    this.apiService.get<WrongAnswer[]>('/analytics/wrong-answers', this.buildParams()).subscribe({
      next: (data) => {
        this.wrongAnswers = data;
        this.loadingWrong = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingWrong = false;
        this.errorMessage = 'Failed to load wrong answer data.';
        this.cdr.markForCheck();
      },
    });
  }

  private loadKnowledgeGaps(): void {
    this.loadingGaps = true;
    this.apiService.get<KnowledgeGap[]>('/analytics/knowledge-gaps', this.buildParams()).subscribe({
      next: (data) => {
        this.knowledgeGaps = data;
        this.loadingGaps = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingGaps = false;
        this.errorMessage = 'Failed to load knowledge gaps.';
        this.cdr.markForCheck();
      },
    });
  }

  private loadItemDifficulty(): void {
    this.loadingDifficulty = true;
    this.apiService.get<ItemDifficulty[]>('/analytics/item-difficulty', this.buildParams()).subscribe({
      next: (data) => {
        this.itemDifficulty = data;
        this.loadingDifficulty = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.loadingDifficulty = false;
        this.errorMessage = 'Failed to load item difficulty data.';
        this.cdr.markForCheck();
      },
    });
  }
}
