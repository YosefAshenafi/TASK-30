import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthStore } from '../../core/stores/auth.store';
import { SessionStore } from '../session.store';
import { NetworkStatusService } from '../../core/stores/network-status.service';
import { ButtonComponent } from '../../shared/ui/button.component';
import { SelectComponent, SelectOption } from '../../shared/ui/select.component';
import { BannerComponent } from '../../shared/ui/banner.component';
import { SessionRecord } from '../../core/db/dexie';
import { catchError, of, Subscription } from 'rxjs';

function uuidv7(): string {
  const now = Date.now();
  const hi = Math.floor(now / 0x100000000);
  const lo = now >>> 0;
  const rand = crypto.getRandomValues(new Uint8Array(10));
  rand[0] = (rand[0] & 0x0f) | 0x70; // version 7
  rand[2] = (rand[2] & 0x3f) | 0x80; // variant
  const hex = [
    (hi >>> 16).toString(16).padStart(4, '0'),
    (hi & 0xffff).toString(16).padStart(4, '0'),
    (lo >>> 16).toString(16).padStart(4, '0'),
    (lo & 0xffff).toString(16).padStart(4, '0'),
    Array.from(rand).map(b => b.toString(16).padStart(2, '0')).join(''),
  ];
  return `${hex[0]}${hex[1]}-${hex[2]}-${hex[3]}-${Array.from(rand.slice(0, 2)).map(b => b.toString(16).padStart(2, '0')).join('')}-${Array.from(rand.slice(2)).map(b => b.toString(16).padStart(2, '0')).join('')}`;
}

@Component({
  selector: 'app-session-new',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ButtonComponent, SelectComponent, BannerComponent],
  template: `
    <div class="p-6 max-w-lg mx-auto">
      <h1 class="text-xl font-bold mb-6">Start New Session</h1>

      @if (!(network.online$ | async)) {
        <app-banner message="Offline — session will be saved locally and synced when back online." severity="warn" />
      }

      @if (errorMessage) {
        <app-banner [message]="errorMessage" severity="error" />
      }

      <form [formGroup]="form" (ngSubmit)="submit()" class="flex flex-col gap-4">
        <app-select
          label="Course"
          placeholder="Select a course…"
          [options]="courseOptions"
          formControlName="courseId" />

        <app-select
          label="Cohort (optional)"
          [placeholder]="cohortPlaceholder"
          [options]="cohortOptions"
          formControlName="cohortId" />

        <div class="flex flex-col gap-1">
          <label class="text-sm font-medium">Default rest time (seconds)</label>
          <input
            formControlName="restSecondsDefault"
            type="number"
            min="0"
            max="600"
            class="border border-[var(--color-border)] rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-[var(--color-brand-500)] min-h-[48px]" />
        </div>

        <app-button type="submit" variant="primary" [loading]="loading" [disabled]="form.invalid">
          Start Session
        </app-button>
      </form>
    </div>
  `,
})
export class SessionNewComponent implements OnInit, OnDestroy {
  form: FormGroup;
  loading = false;
  errorMessage = '';
  courseOptions: SelectOption[] = [];
  cohortOptions: SelectOption[] = [];
  cohortPlaceholder = 'Select a course first…';
  private sub = new Subscription();

  constructor(
    private fb: FormBuilder,
    private http: HttpClient,
    private authStore: AuthStore,
    private sessionStore: SessionStore,
    private router: Router,
    readonly network: NetworkStatusService,
  ) {
    this.form = this.fb.group({
      courseId: ['', Validators.required],
      cohortId: [''],
      restSecondsDefault: [60, [Validators.required, Validators.min(0), Validators.max(600)]],
    });
  }

  ngOnInit(): void {
    this.http.get<{ content: { id: string; title: string }[] }>('/api/v1/courses?size=100').pipe(
      catchError(() => of({ content: [] }))
    ).subscribe(r => {
      this.courseOptions = r.content.map(c => ({ value: c.id, label: c.title }));
    });

    const cohortCtrl = this.form.get('cohortId')!;
    cohortCtrl.disable();

    this.sub.add(
      this.form.get('courseId')!.valueChanges.subscribe((courseId: string) => {
        cohortCtrl.setValue('');
        this.cohortOptions = [];

        if (!courseId) {
          cohortCtrl.disable();
          this.cohortPlaceholder = 'Select a course first…';
          return;
        }

        this.cohortPlaceholder = 'Loading…';
        this.http.get<{ id: string; name: string }[]>(`/api/v1/courses/${courseId}/cohorts`).pipe(
          catchError(() => of([]))
        ).subscribe(cohorts => {
          this.cohortOptions = cohorts.map(c => ({ value: c.id, label: c.name }));
          this.cohortPlaceholder = cohorts.length ? 'No cohort' : 'No cohorts available';
          cohortCtrl.enable();
        });
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  submit(): void {
    if (this.form.invalid || this.loading) return;
    this.loading = true;
    this.errorMessage = '';

    const sessionId = uuidv7();
    const now = new Date().toISOString();
    const { courseId, cohortId, restSecondsDefault } = this.form.value;
    const userId = this.authStore.userId()!;

    const session: SessionRecord = {
      id: sessionId,
      studentId: userId,
      courseId,
      cohortId: cohortId || null,
      startedAt: now,
      endedAt: null,
      restSecondsDefault: Number(restSecondsDefault),
      status: 'IN_PROGRESS',
      clientUpdatedAt: now,
    };

    // Write to local DB immediately
    this.sessionStore.upsertSession(session).then(() => {
      // Also attempt immediate API create
      this.http.post(`/api/v1/sessions`, {
        id: sessionId,
        studentId: userId,
        courseId,
        cohortId: cohortId || null,
        restSecondsDefault: Number(restSecondsDefault),
        clientUpdatedAt: now,
      }, {
        headers: { 'Idempotency-Key': sessionId },
      }).pipe(catchError(() => of(null))).subscribe(() => {
        this.loading = false;
        this.router.navigate(['/sessions', sessionId, 'run']);
      });
    });
  }
}
