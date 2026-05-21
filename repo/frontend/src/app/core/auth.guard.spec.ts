import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  function buildRoute(roles?: string[]): ActivatedRouteSnapshot {
    const route = new ActivatedRouteSnapshot();
    (route as any).data = roles ? { roles } : {};
    return route;
  }

  function runGuard(route: ActivatedRouteSnapshot): boolean {
    return TestBed.runInInjectionContext(() =>
      authGuard(route, {} as RouterStateSnapshot)
    ) as boolean;
  }

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['isAuthenticated', 'hasRole']);
    router = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows navigation when authenticated and no roles required', () => {
    authService.isAuthenticated.and.returnValue(true);
    expect(runGuard(buildRoute())).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects to /login when not authenticated', () => {
    authService.isAuthenticated.and.returnValue(false);
    expect(runGuard(buildRoute())).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('allows navigation when authenticated and has required role', () => {
    authService.isAuthenticated.and.returnValue(true);
    authService.hasRole.and.returnValue(true);
    expect(runGuard(buildRoute(['ADMINISTRATOR']))).toBeTrue();
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('redirects to /dashboard when authenticated but lacks required role', () => {
    authService.isAuthenticated.and.returnValue(true);
    authService.hasRole.and.returnValue(false);
    expect(runGuard(buildRoute(['ADMINISTRATOR']))).toBeFalse();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('allows navigation when authenticated and empty roles array', () => {
    authService.isAuthenticated.and.returnValue(true);
    expect(runGuard(buildRoute([]))).toBeTrue();
  });
});
