import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { ReportsCenterComponent } from './reports-center.component';
import { ApiService } from '../core/api.service';

describe('ReportsCenterComponent', () => {
  let apiService: jasmine.SpyObj<ApiService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  // ngOnInit() calls subscribeToSSE(), which opens a real EventSource connection.
  // In a fakeAsync zone that real network task leaks and destabilizes tick()/flush(),
  // so stub the global EventSource with an inert fake that never opens a socket.
  let realEventSource: typeof EventSource;
  class FakeEventSource {
    onmessage: ((e: MessageEvent) => void) | null = null;
    onerror: ((e: Event) => void) | null = null;
    onopen: ((e: Event) => void) | null = null;
    constructor(public url: string) {}
    close(): void {}
  }

  const enrollments = [
    {
      enrollmentId: 'e1',
      studentName: 'Alice',
      courseName: 'Safety 101',
      enrolledAt: '2025-01-01T00:00:00Z',
      status: 'ACTIVE',
    },
  ];
  const notifications = [
    { id: 'n1', title: 'Test', message: 'Hello', read: false, createdAt: '2025-01-01T00:00:00Z' },
  ];

  beforeEach(() => {
    realEventSource = (window as any).EventSource;
    (window as any).EventSource = FakeEventSource as unknown as typeof EventSource;

    apiService = jasmine.createSpyObj('ApiService', ['get', 'post']);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    apiService.get.and.callFake((url: string): any => {
      // Return a fresh copy: a sibling test marks notifications as read in place, which would
      // otherwise mutate this shared fixture and make unreadCount flaky under random test order.
      if (url === '/notifications') return of(notifications.map((n) => ({ ...n })));
      if (url.includes('/reports/enrollments')) return of(enrollments);
      return of([]);
    });

    TestBed.configureTestingModule({
      imports: [ReportsCenterComponent],
      providers: [
        provideNoopAnimations(),
        { provide: ApiService, useValue: apiService },
        { provide: MatDialog, useValue: dialog },
        { provide: MatSnackBar, useValue: snackBar },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    // Force the mocked Material services (providedIn 'root' is otherwise shadowed by the
    // standalone component's own injector, so the component would use the real services).
    TestBed.overrideProvider(MatSnackBar, { useValue: snackBar });
    TestBed.overrideProvider(MatDialog, { useValue: dialog });
  });

  afterEach(() => {
    (window as any).EventSource = realEventSource;
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(ReportsCenterComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads enrollments and notifications on init', fakeAsync(() => {
    const fixture = TestBed.createComponent(ReportsCenterComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.enrollments).toEqual(enrollments);
    expect(fixture.componentInstance.notifications).toEqual(notifications);
    expect(fixture.componentInstance.unreadCount).toBe(1);
  }));

  it('toggleNotifications() shows panel and marks all as read', fakeAsync(() => {
    const fixture = TestBed.createComponent(ReportsCenterComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    expect(comp.showNotifications).toBeFalse();

    comp.toggleNotifications();
    fixture.detectChanges();

    expect(comp.showNotifications).toBeTrue();
    expect(comp.unreadCount).toBe(0);
    expect(comp.notifications.every((n) => n.read)).toBeTrue();
  }));

  it('exportReport() calls POST /reports/export with correct body', fakeAsync(() => {
    apiService.post.and.returnValue(of({ downloadPath: '/files/report.csv' }));
    const fixture = TestBed.createComponent(ReportsCenterComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.exportReport('ENROLLMENTS', 'CSV');
    tick();

    expect(apiService.post).toHaveBeenCalledWith('/reports/export', {
      reportType: 'ENROLLMENTS',
      format: 'CSV',
    });
    expect(snackBar.open).toHaveBeenCalledWith(
      jasmine.stringContaining('/files/report.csv'),
      'Dismiss',
      jasmine.any(Object)
    );
  }));

  it('exportReport() shows error snack on failure', fakeAsync(() => {
    apiService.post.and.returnValue(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(ReportsCenterComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.exportReport('REFUNDS', 'PDF');
    tick();

    expect(snackBar.open).toHaveBeenCalledWith(
      'Export failed. Please try again.',
      'Dismiss',
      jasmine.any(Object)
    );
  }));

  it('openScheduleDialog() opens dialog and posts schedule on close', fakeAsync(() => {
    const dialogResult = { reportType: 'ENROLLMENTS', cronExpression: '0 9 * * MON', email: '' };
    const dialogRef = { afterClosed: () => of(dialogResult) } as any;
    dialog.open.and.returnValue(dialogRef);
    apiService.post.and.returnValue(of({}));

    const fixture = TestBed.createComponent(ReportsCenterComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.openScheduleDialog('ENROLLMENTS');
    tick();

    expect(dialog.open).toHaveBeenCalled();
    expect(apiService.post).toHaveBeenCalledWith(
      '/reports/schedule',
      jasmine.objectContaining({ cronExpression: '0 9 * * MON' })
    );
    expect(snackBar.open).toHaveBeenCalledWith(
      'Report scheduled successfully.',
      'Dismiss',
      jasmine.any(Object)
    );
  }));

  it('onTabChange() loads seat utilization for index 1', fakeAsync(() => {
    apiService.get.and.callFake((url: string): any => {
      if (url === '/notifications') return of([]);
      if (url.includes('seat-utilization')) return of([]);
      return of([]);
    });
    const fixture = TestBed.createComponent(ReportsCenterComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.onTabChange({ index: 1 });
    tick();

    expect(apiService.get).toHaveBeenCalledWith(
      jasmine.stringContaining('/reports/seat-utilization'),
      jasmine.any(Object)
    );
  }));

  it('sets errorMessage when enrollments load fails', fakeAsync(() => {
    apiService.get.and.callFake((url: string): any => {
      if (url === '/notifications') return of([]);
      return throwError(() => new Error('fail'));
    });

    const fixture = TestBed.createComponent(ReportsCenterComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.errorMessage).toBe('Failed to load enrollments.');
  }));

  it('ngOnDestroy() closes SSE subscription', () => {
    const fixture = TestBed.createComponent(ReportsCenterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    const closeSpy = jasmine.createSpy('close');
    (comp as any).sseSubscription = { close: closeSpy } as any;

    fixture.destroy();

    expect(closeSpy).toHaveBeenCalled();
  });
});
