import { TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { BehaviorSubject, of, Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { AuthService, UserInfo } from './core/auth.service';

describe('AppComponent', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: Router;
  let userSubject: BehaviorSubject<UserInfo | null>;

  const adminUser: UserInfo = {
    userId: 'u1',
    username: 'admin',
    role: 'ROLE_ADMINISTRATOR',
    organizationId: null,
  };

  // Emit a NavigationEnd through the real Router's event stream so the
  // component's NavigationEnd subscription runs exactly as in production.
  const emitNavigationEnd = (event: NavigationEnd): void => {
    (router.events as unknown as Subject<NavigationEnd>).next(event);
  };

  beforeEach(() => {
    userSubject = new BehaviorSubject<UserInfo | null>(null);
    authService = jasmine.createSpyObj('AuthService', ['hasRole', 'logout']);
    (authService as any).currentUser$ = userSubject.asObservable();
    authService.logout.and.returnValue(of(undefined));

    TestBed.configureTestingModule({
      // RouterTestingModule supplies a real Router AND ActivatedRoute so the
      // component's RouterOutlet/RouterLink usage renders during detectChanges().
      imports: [AppComponent, RouterTestingModule],
      providers: [
        provideNoopAnimations(),
        { provide: AuthService, useValue: authService },
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    router = TestBed.inject(Router);
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
    // Explicit type arg: TS narrows `emitted` to `null` (the only synchronous assignment),
    // so without it jasmine infers the matcher type as `null` and rejects a UserInfo expected value.
    expect<UserInfo | null>(emitted).toEqual(adminUser);
  });

  it('isMenuVisible is false initially (before NavigationEnd fires)', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isMenuVisible).toBeFalse();
  });

  it('isMenuVisible becomes true for non-auth routes after NavigationEnd', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    emitNavigationEnd(new NavigationEnd(1, '/dashboard', '/dashboard'));
    expect(fixture.componentInstance.isMenuVisible).toBeTrue();
  });

  it('isMenuVisible stays false for /login route', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    emitNavigationEnd(new NavigationEnd(1, '/login', '/login'));
    expect(fixture.componentInstance.isMenuVisible).toBeFalse();
  });

  it('isMenuVisible stays false for /register route', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    emitNavigationEnd(new NavigationEnd(1, '/register', '/register'));
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
