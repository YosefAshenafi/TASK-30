import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { AuthService } from '../core/auth.service';
import { ApiService } from '../core/api.service';

describe('DashboardComponent', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let apiService: jasmine.SpyObj<ApiService>;

  const mockSummary = {
    pendingApprovalsCount: 3,
    recentAnomaliesCount: 1,
    totalSessions: 42,
    activeSessions: 5,
  };

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['hasRole', 'getUserId']);
    apiService = jasmine.createSpyObj('ApiService', ['get']);

    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: ApiService, useValue: apiService },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });
  });

  it('should be created', () => {
    authService.hasRole.and.returnValue(false);
    apiService.get.and.returnValue(of(mockSummary));
    const fixture = TestBed.createComponent(DashboardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads student summary endpoint when user is STUDENT', fakeAsync(() => {
    authService.hasRole.and.callFake((role: string) => role === 'STUDENT');
    apiService.get.and.returnValue(of(mockSummary));

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    tick();

    expect(apiService.get).toHaveBeenCalledWith('/dashboard/student-summary');
    expect(fixture.componentInstance.summary).toEqual(mockSummary);
    expect(fixture.componentInstance.loading).toBeFalse();
  }));

  it('loads admin summary endpoint when user is admin/mentor', fakeAsync(() => {
    authService.hasRole.and.callFake((role: string) => role === 'ADMINISTRATOR');
    apiService.get.and.returnValue(of(mockSummary));

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    tick();

    expect(apiService.get).toHaveBeenCalledWith('/dashboard/summary');
    expect(fixture.componentInstance.summary).toEqual(mockSummary);
  }));

  it('sets errorMessage on API failure', fakeAsync(() => {
    authService.hasRole.and.returnValue(false);
    apiService.get.and.returnValue(throwError(() => new Error('network error')));

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.errorMessage).toBe('Failed to load dashboard data.');
    expect(fixture.componentInstance.loading).toBeFalse();
  }));

  it('isStudent() delegates to authService.hasRole with STUDENT', () => {
    authService.hasRole.and.returnValue(true);
    apiService.get.and.returnValue(of(mockSummary));

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    expect(comp.isStudent()).toBeTrue();
    expect(authService.hasRole).toHaveBeenCalledWith('STUDENT');
  });

  it('isAdmin() delegates to authService.hasRole with ADMINISTRATOR', () => {
    authService.hasRole.and.returnValue(false);
    apiService.get.and.returnValue(of(mockSummary));

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    comp.isAdmin();
    expect(authService.hasRole).toHaveBeenCalledWith('ADMINISTRATOR');
  });

  it('isMentorOrAdmin() checks all mentor/admin roles', () => {
    authService.hasRole.and.returnValue(false);
    apiService.get.and.returnValue(of(mockSummary));

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    comp.isMentorOrAdmin();
    expect(authService.hasRole).toHaveBeenCalledWith('FACULTY_MENTOR', 'CORPORATE_MENTOR', 'ADMINISTRATOR');
  });
});
