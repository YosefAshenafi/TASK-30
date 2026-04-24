import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { orgScopeGuard } from '../../web/src/app/core/guards/org-scope.guard';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';

describe('orgScopeGuard', () => {
  let authStore: AuthStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [RouterTestingModule],
    });
    authStore = TestBed.inject(AuthStore);
  });

  it('returns true for corporate mentor with organization scope', () => {
    spyOn(authStore, 'userRole').and.returnValue('CORPORATE_MENTOR');
    spyOn(authStore, 'organizationId').and.returnValue('00000000-0000-0000-0000-000000000001');

    const result = TestBed.runInInjectionContext(() =>
      orgScopeGuard({} as ActivatedRouteSnapshot, { url: '/analytics/mastery' } as RouterStateSnapshot)
    );

    expect(result).toBeTrue();
  });

  it('redirects to /home when mentor has no organization scope', () => {
    spyOn(authStore, 'userRole').and.returnValue('CORPORATE_MENTOR');
    spyOn(authStore, 'organizationId').and.returnValue(null);

    const result = TestBed.runInInjectionContext(() =>
      orgScopeGuard({} as ActivatedRouteSnapshot, { url: '/analytics/mastery' } as RouterStateSnapshot)
    );

    const urlTree = result as ReturnType<Router['createUrlTree']>;
    expect(urlTree.toString()).toContain('/home');
  });
});
