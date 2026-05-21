import { TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService, UserInfo } from './core/auth.service';

describe('AppComponent', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: { events: Subject<any>; navigate: jasmine.Spy };
  let userSubject: BehaviorSubject<UserInfo | null>;

  const adminUser: UserInfo = {
    userId: 'u1',
    username: 'admin',
    role: 'ROLE_ADMINISTRATOR',
    organizationId: null,
  };

  beforeEach(() => {
    userSubject = new BehaviorSubject<UserInfo | null>(null);
    authService = jasmine.createSpyObj('AuthService', ['hasRole', 'logout']);
    (authService as any).currentUser$ = userSubject.asObservable();
    authService.logout.and.returnValue(of(undefined));

    router = {
      events: new Subject<any>(),
      navigate: jasmine.createSpy('navigate'),
    };

    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('currentUser$ reflects the authService stream', () => {
    userSubject.next(adminUser);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    let emitted: UserInfo | null = null;
    fixture.componentInstance.currentUser$.subscribe((u) => (emitted = u));
    expect(emitted).toEqual(adminUser);
  });

  it('isMenuVisible is false initially (before NavigationEnd fires)', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isMenuVisible).toBeFalse();
  });

  it('isMenuVisible becomes true for non-auth routes after NavigationEnd', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const event = new NavigationEnd(1, '/dashboard', '/dashboard');
    router.events.next(event);
    expect(fixture.componentInstance.isMenuVisible).toBeTrue();
  });

  it('isMenuVisible stays false for /login route', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    router.events.next(new NavigationEnd(1, '/login', '/login'));
    expect(fixture.componentInstance.isMenuVisible).toBeFalse();
  });

  it('isMenuVisible stays false for /register route', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    router.events.next(new NavigationEnd(1, '/register', '/register'));
    expect(fixture.componentInstance.isMenuVisible).toBeFalse();
  });

  it('isStudent() delegates to authService.hasRole with STUDENT', () => {
    authService.hasRole.and.returnValue(true);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const result = fixture.componentInstance.isStudent();
    expect(result).toBeTrue();
    expect(authService.hasRole).toHaveBeenCalledWith('STUDENT');
  });

  it('isAdmin() delegates to authService.hasRole with ADMINISTRATOR', () => {
    authService.hasRole.and.returnValue(false);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    fixture.componentInstance.isAdmin();
    expect(authService.hasRole).toHaveBeenCalledWith('ADMINISTRATOR');
  });

  it('isMentorOrAdmin() checks all mentor/admin roles', () => {
    authService.hasRole.and.returnValue(false);
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    fixture.componentInstance.isMentorOrAdmin();
    expect(authService.hasRole).toHaveBeenCalledWith(
      'FACULTY_MENTOR',
      'CORPORATE_MENTOR',
      'ADMINISTRATOR'
    );
  });

  it('logout() calls authService.logout()', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    fixture.componentInstance.logout();
    expect(authService.logout).toHaveBeenCalled();
  });
});
