import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { SessionCaptureComponent } from './session-capture.component';
import { SyncService } from '../core/sync.service';
import { DbService, MeridianDb } from '../core/db.service';
import { ApiService } from '../core/api.service';

describe('SessionCaptureComponent', () => {
  let component: SessionCaptureComponent;
  let fixture: ComponentFixture<SessionCaptureComponent>;
  let syncServiceSpy: jasmine.SpyObj<SyncService>;
  let dbServiceSpy: jasmine.SpyObj<DbService>;
  let apiServiceSpy: jasmine.SpyObj<ApiService>;
  let offlineModeSubject: BehaviorSubject<boolean>;

  const mockSessionDetail = {
    id: 'session-123',
    courseId: 'course-1',
    courseName: 'Fire Safety Level 1',
    status: 'IN_PROGRESS',
    restTimerSecs: 60,
    startedAt: new Date().toISOString(),
    activities: [
      { activityRef: 'act-1', name: 'Introduction', completed: false },
      { activityRef: 'act-2', name: 'Practical Exercise', completed: false },
    ],
    idempotencyKey: 'idem-key-abc',
  };

  beforeEach(async () => {
    offlineModeSubject = new BehaviorSubject<boolean>(false);

    syncServiceSpy = jasmine.createSpyObj<SyncService>(
      'SyncService',
      ['isOffline'],
      { offlineMode$: offlineModeSubject.asObservable() }
    );
    syncServiceSpy.isOffline.and.returnValue(false);

    const mockSessionsTable = {
      get: jasmine.createSpy('get').and.returnValue(Promise.resolve(null)),
      put: jasmine.createSpy('put').and.returnValue(Promise.resolve()),
    };
    const mockDb = { sessions: mockSessionsTable } as unknown as MeridianDb;
    dbServiceSpy = jasmine.createSpyObj<DbService>('DbService', [], { db: mockDb });

    apiServiceSpy = jasmine.createSpyObj<ApiService>('ApiService', [
      'get',
      'post',
      'put',
      'patch',
      'delete',
    ]);
    apiServiceSpy.get.and.returnValue(of(mockSessionDetail));

    await TestBed.configureTestingModule({
      imports: [
        SessionCaptureComponent,
        ReactiveFormsModule,
        HttpClientTestingModule,
        RouterTestingModule,
        NoopAnimationsModule,
      ],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: () => 'session-123' } },
          },
        },
        { provide: SyncService, useValue: syncServiceSpy },
        { provide: DbService, useValue: dbServiceSpy },
        { provide: ApiService, useValue: apiServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionCaptureComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('renders elapsed timer', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const timerEl = compiled.querySelector('[data-testid="elapsed-timer"]');
    expect(timerEl).not.toBeNull();
    expect(timerEl!.textContent).toMatch(/\d{2}:\d{2}:\d{2}/);
  });

  it('shows offline banner when navigator.onLine is false', fakeAsync(() => {
    offlineModeSubject.next(true);
    syncServiceSpy.isOffline.and.returnValue(true);
    component.isOffline = true;

    tick();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const banner = compiled.querySelector('.offline-banner');
    expect(banner).not.toBeNull();
    expect(banner!.textContent).toContain('Offline');
  }));

  it('validates rest timer bounds', () => {
    const restControl = component.restTimerForm.get('restTimerSecs')!;

    restControl.setValue(5);
    restControl.markAsTouched();
    restControl.updateValueAndValidity();
    expect(restControl.hasError('outOfRange')).toBeTrue();

    restControl.setValue(301);
    restControl.updateValueAndValidity();
    expect(restControl.hasError('outOfRange')).toBeTrue();

    restControl.setValue(120);
    restControl.updateValueAndValidity();
    expect(restControl.valid).toBeTrue();
    expect(restControl.hasError('outOfRange')).toBeFalse();
  });

  it('saves to IndexedDB on offline activity check', fakeAsync(() => {
    component.isOffline = true;
    syncServiceSpy.isOffline.and.returnValue(true);

    component.session = {
      id: 'session-123',
      courseId: 'course-1',
      courseName: 'Fire Safety Level 1',
      status: 'IN_PROGRESS',
      restTimerSecs: 60,
      startedAt: new Date().toISOString(),
      activities: [
        { activityRef: 'act-1', name: 'Introduction', completed: false },
        { activityRef: 'act-2', name: 'Practical Exercise', completed: false },
      ],
      idempotencyKey: 'idem-key-abc',
    };

    component.updateActivity(0, true);
    tick();

    expect((dbServiceSpy.db.sessions.put as jasmine.Spy).calls.count()).toBeGreaterThanOrEqual(1);
  }));
});
