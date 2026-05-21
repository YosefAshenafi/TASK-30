import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of, throwError } from 'rxjs';
import { SessionListComponent, SessionSummary } from './session-list.component';
import { ApiService } from '../core/api.service';

describe('SessionListComponent', () => {
  let apiService: jasmine.SpyObj<ApiService>;

  const sessions: SessionSummary[] = [
    {
      id: 's1',
      courseId: 'c1',
      courseName: 'Safety 101',
      status: 'IN_PROGRESS',
      startedAt: '2025-01-01T10:00:00Z',
    },
    {
      id: 's2',
      courseId: 'c2',
      courseName: 'First Aid',
      status: 'COMPLETED',
      startedAt: '2025-01-02T09:00:00Z',
      completedAt: '2025-01-02T10:30:00Z',
    },
  ];

  beforeEach(() => {
    apiService = jasmine.createSpyObj('ApiService', ['get']);

    TestBed.configureTestingModule({
      imports: [SessionListComponent],
      providers: [{ provide: ApiService, useValue: apiService }],
      schemas: [NO_ERRORS_SCHEMA],
    });
  });

  it('should be created', () => {
    apiService.get.and.returnValue(of(sessions));
    const fixture = TestBed.createComponent(SessionListComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads sessions on init via GET /sessions', fakeAsync(() => {
    apiService.get.and.returnValue(of(sessions));
    const fixture = TestBed.createComponent(SessionListComponent);
    fixture.detectChanges();
    tick();

    expect(apiService.get).toHaveBeenCalledWith('/sessions');
    expect(fixture.componentInstance.sessions).toEqual(sessions);
    expect(fixture.componentInstance.loading).toBeFalse();
  }));

  it('shows empty state when no sessions are returned', fakeAsync(() => {
    apiService.get.and.returnValue(of([]));
    const fixture = TestBed.createComponent(SessionListComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.sessions.length).toBe(0);
  }));

  it('sets errorMessage when API call fails', fakeAsync(() => {
    apiService.get.and.returnValue(throwError(() => new Error('network error')));
    const fixture = TestBed.createComponent(SessionListComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.errorMessage).toBe('Failed to load sessions.');
    expect(fixture.componentInstance.loading).toBeFalse();
  }));

  it('isResumable() returns true for IN_PROGRESS and INTERRUPTED', () => {
    apiService.get.and.returnValue(of([]));
    const fixture = TestBed.createComponent(SessionListComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    expect(comp.isResumable('IN_PROGRESS')).toBeTrue();
    expect(comp.isResumable('INTERRUPTED')).toBeTrue();
    expect(comp.isResumable('COMPLETED')).toBeFalse();
    expect(comp.isResumable('VERIFIED')).toBeFalse();
    expect(comp.isResumable('PENDING')).toBeFalse();
  });

  it('getStatusClass() returns kebab-case CSS class', () => {
    apiService.get.and.returnValue(of([]));
    const fixture = TestBed.createComponent(SessionListComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    expect(comp.getStatusClass('IN_PROGRESS')).toBe('status-in-progress');
    expect(comp.getStatusClass('COMPLETED')).toBe('status-completed');
    expect(comp.getStatusClass('PENDING')).toBe('status-pending');
    expect(comp.getStatusClass('INTERRUPTED')).toBe('status-interrupted');
    expect(comp.getStatusClass('VERIFIED')).toBe('status-verified');
  });

  it('displayedColumns contains expected column names', fakeAsync(() => {
    apiService.get.and.returnValue(of(sessions));
    const fixture = TestBed.createComponent(SessionListComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.displayedColumns).toEqual([
      'courseName',
      'status',
      'startedAt',
      'actions',
    ]);
  }));
});
