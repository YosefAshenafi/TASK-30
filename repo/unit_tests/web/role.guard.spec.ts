import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { roleGuard } from '../../web/src/app/core/guards/role.guard';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';

describe('roleGuard', () => {
  let authStore: AuthStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
    });
    authStore = TestBed.inject(AuthStore);
  });

  it('returns true when no role is required', () => {
    const route = { data: {} } as ActivatedRouteSnapshot;
    const state = { url: '/home' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
    expect(result).toBeTrue();
  });

  it('returns true when user role matches required role', () => {
    const route = { data: { roles: ['ADMIN'] } } as ActivatedRouteSnapshot;
    const state = { url: '/admin/users' } as RouterStateSnapshot;
    spyOn(authStore, 'userRole').and.returnValue('ADMIN');

    const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
    expect(result).toBeTrue();
  });

  it('redirects to /home when role does not match', () => {
    const route = { data: { roles: ['ADMIN'] } } as ActivatedRouteSnapshot;
    const state = { url: '/admin/users' } as RouterStateSnapshot;
    spyOn(authStore, 'userRole').and.returnValue('STUDENT');

    const result = TestBed.runInInjectionContext(() => roleGuard(route, state));
    const urlTree = result as ReturnType<Router['createUrlTree']>;
    expect(urlTree.toString()).toContain('/home');
  });
});
