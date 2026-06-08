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
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule, MatSnackBar } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { ApiService } from '../core/api.service';

interface Course {
  id: string;
  title: string;
  version: string;
  location: string;
  instructor: string;
}

/**
 * Course Management — lets Administrators and Faculty Mentors create and edit the courses that
 * sessions, enrollments, analytics and reports are built on. Backed by CourseController
 * (GET/POST/PUT /api/courses), which restricts mutations to ADMINISTRATOR / FACULTY_MENTOR.
 */
@Component({
  selector: 'app-course-management',
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
    MatProgressSpinnerModule,
    MatIconModule,
    MatSnackBarModule,
    MatDividerModule,
  ],
  template: `
    <div class="course-container">
      <h1 class="page-title">Course Management</h1>

      <!-- Create / Edit form -->
      <mat-card class="section-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>{{ editingId ? 'edit' : 'add_circle' }}</mat-icon>
          <mat-card-title>{{ editingId ? 'Edit Course' : 'New Course' }}</mat-card-title>
          <mat-card-subtitle>
            {{ editingId ? 'Update the selected course' : 'Add a course to the catalog' }}
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="courseForm" (ngSubmit)="save()" class="course-form">
            <mat-form-field appearance="outline" class="field-title">
              <mat-label>Title</mat-label>
              <input matInput formControlName="title" />
              <mat-error *ngIf="courseForm.get('title')?.hasError('required')">
                Title is required
              </mat-error>
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Version</mat-label>
              <input matInput formControlName="version" placeholder="1.0" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Location</mat-label>
              <input matInput formControlName="location" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Instructor</mat-label>
              <input matInput formControlName="instructor" />
            </mat-form-field>
            <mat-form-field appearance="outline" class="field-capacity">
              <mat-label>Capacity</mat-label>
              <input matInput type="number" formControlName="capacity" min="0" />
            </mat-form-field>

            <div class="form-actions">
              <button mat-raised-button color="primary" type="submit" [disabled]="courseForm.invalid || saving">
                <mat-spinner diameter="20" *ngIf="saving"></mat-spinner>
                <mat-icon *ngIf="!saving">{{ editingId ? 'save' : 'add' }}</mat-icon>
                {{ editingId ? 'Save Changes' : 'Create Course' }}
              </button>
              <button mat-stroked-button type="button" *ngIf="editingId" (click)="cancelEdit()">
                Cancel
              </button>
            </div>
          </form>
        </mat-card-content>
      </mat-card>

      <mat-divider class="section-divider"></mat-divider>

      <!-- Course list -->
      <mat-card class="section-card">
        <mat-card-header>
          <mat-icon mat-card-avatar>menu_book</mat-icon>
          <mat-card-title>Courses</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <div *ngIf="loading" class="loading-center">
            <mat-spinner diameter="40"></mat-spinner>
          </div>
          <div class="empty-state" *ngIf="!loading && courses.length === 0">
            <mat-icon>menu_book</mat-icon>
            <p>No courses yet. Create one above.</p>
          </div>
          <table
            mat-table
            [dataSource]="courses"
            class="full-width-table"
            *ngIf="!loading && courses.length > 0"
          >
            <ng-container matColumnDef="title">
              <th mat-header-cell *matHeaderCellDef>Title</th>
              <td mat-cell *matCellDef="let r">{{ r.title }}</td>
            </ng-container>
            <ng-container matColumnDef="version">
              <th mat-header-cell *matHeaderCellDef>Version</th>
              <td mat-cell *matCellDef="let r">{{ r.version }}</td>
            </ng-container>
            <ng-container matColumnDef="location">
              <th mat-header-cell *matHeaderCellDef>Location</th>
              <td mat-cell *matCellDef="let r">{{ r.location || '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="instructor">
              <th mat-header-cell *matHeaderCellDef>Instructor</th>
              <td mat-cell *matCellDef="let r">{{ r.instructor || '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef>Actions</th>
              <td mat-cell *matCellDef="let r">
                <button mat-stroked-button color="primary" (click)="editCourse(r)">
                  <mat-icon>edit</mat-icon>
                  Edit
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let r; columns: columns"></tr>
          </table>
        </mat-card-content>
      </mat-card>

      <div class="error-banner" *ngIf="errorMessage" role="alert">{{ errorMessage }}</div>
    </div>
  `,
  styles: [
    `
      .course-container { max-width: 960px; margin: 0 auto; }
      .page-title { font-size: 28px; font-weight: 500; margin-bottom: 24px; }
      .section-card { margin-bottom: 16px; }
      .section-divider { margin: 16px 0; }
      .course-form { display: flex; gap: 16px; align-items: flex-start; flex-wrap: wrap; padding-top: 8px; }
      .course-form mat-form-field { min-width: 180px; }
      .field-title { min-width: 260px; }
      .field-capacity { min-width: 120px; }
      .form-actions { display: flex; gap: 12px; align-items: center; margin-top: 6px; }
      .loading-center { display: flex; justify-content: center; padding: 32px; }
      .full-width-table { width: 100%; }
      .empty-state { text-align: center; padding: 48px; color: rgba(0,0,0,0.4); }
      .empty-state mat-icon { font-size: 48px; height: 48px; width: 48px; }
      .error-banner { background: #f44336; color: white; padding: 8px 16px; border-radius: 4px; margin-top: 12px; }
    `,
  ],
})
export class CourseManagementComponent implements OnInit {
  courses: Course[] = [];
  columns = ['title', 'version', 'location', 'instructor', 'actions'];

  loading = false;
  saving = false;
  editingId: string | null = null;
  errorMessage = '';

  courseForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    private apiService: ApiService,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {
    this.courseForm = this.fb.group({
      title: ['', [Validators.required]],
      version: ['1.0'],
      location: [''],
      instructor: [''],
      capacity: [null],
    });
  }

  ngOnInit(): void {
    this.loadCourses();
  }

  save(): void {
    if (this.courseForm.invalid) return;
    this.saving = true;
    const payload = this.courseForm.value;
    const request$ = this.editingId
      ? this.apiService.put<Course>(`/courses/${this.editingId}`, payload)
      : this.apiService.post<Course>('/courses', payload);

    request$.subscribe({
      next: () => {
        this.saving = false;
        const verb = this.editingId ? 'updated' : 'created';
        this.snackBar.open(`Course ${verb}.`, 'Dismiss', { duration: 3000 });
        this.cancelEdit();
        this.loadCourses();
        this.cdr.markForCheck();
      },
      error: () => {
        this.saving = false;
        this.snackBar.open('Failed to save course.', 'Dismiss', { duration: 3000 });
        this.cdr.markForCheck();
      },
    });
  }

  editCourse(course: Course): void {
    this.editingId = course.id;
    this.courseForm.patchValue({
      title: course.title,
      version: course.version,
      location: course.location,
      instructor: course.instructor,
      capacity: null,
    });
  }

  cancelEdit(): void {
    this.editingId = null;
    this.courseForm.reset({ version: '1.0', capacity: null });
  }

  private loadCourses(): void {
    this.loading = true;
    // GET /courses returns a Spring Page ({ content: [...] }); unwrap it.
    this.apiService
      .get<Course[] | { content: Course[] }>('/courses')
      .subscribe({
        next: (d) => {
          this.courses = Array.isArray(d) ? d : d.content ?? [];
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.loading = false;
          this.errorMessage = 'Failed to load courses.';
          this.cdr.markForCheck();
        },
      });
  }
}
