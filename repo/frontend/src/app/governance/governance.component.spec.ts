import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { GovernanceComponent } from './governance.component';
import { ApiService } from '../core/api.service';

describe('GovernanceComponent', () => {
  let apiService: jasmine.SpyObj<ApiService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const users = [
    { userId: 'u1', username: 'alice' },
    { userId: 'u2', username: 'bob' },
  ];
  const permissions = [
    {
      id: 'p1',
      fieldName: 'ssn',
      classification: 'CONFIDENTIAL',
      grantedBy: 'admin',
      createdAt: '2025-01-01T00:00:00Z',
    },
  ];

  beforeEach(() => {
    apiService = jasmine.createSpyObj('ApiService', ['get', 'post', 'put']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    apiService.get.and.callFake((url: string): any => {
      if (url === '/admin/users') return of(users);
      if (url.includes('/governance/users/')) return of(permissions);
      return of([]);
    });

    TestBed.configureTestingModule({
      imports: [GovernanceComponent, ReactiveFormsModule],
      providers: [
        { provide: ApiService, useValue: apiService },
        { provide: MatSnackBar, useValue: snackBar },
        provideNoopAnimations(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    // Force the MatSnackBar mock (providedIn 'root' is otherwise shadowed by the component injector).
    TestBed.overrideProvider(MatSnackBar, { useValue: snackBar });
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(GovernanceComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads users on init', fakeAsync(() => {
    const fixture = TestBed.createComponent(GovernanceComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.users).toEqual(
      users.map((u) => ({ userId: u.userId, username: u.username }))
    );
    expect(fixture.componentInstance.loadingUsers).toBeFalse();
  }));

  it('onUserSelect() sets selectedUserId and loads permissions', fakeAsync(() => {
    const fixture = TestBed.createComponent(GovernanceComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.onUserSelect('u1');
    tick();

    expect(fixture.componentInstance.selectedUserId).toBe('u1');
    expect(fixture.componentInstance.permissions).toEqual(permissions);
    expect(fixture.componentInstance.loadingPerms).toBeFalse();
    expect(apiService.get).toHaveBeenCalledWith('/governance/users/u1/permissions');
  }));

  it('grantPermission() does nothing when form is invalid', fakeAsync(() => {
    const fixture = TestBed.createComponent(GovernanceComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.selectedUserId = 'u1';
    comp.grantForm.get('fieldName')!.setValue('');
    comp.grantPermission();
    tick();

    expect(apiService.put).not.toHaveBeenCalled();
  }));

  it('grantPermission() PUTs permission and reloads the list', fakeAsync(() => {
    apiService.put.and.returnValue(of({}));
    // After granting, the component reloads permissions from the server.
    apiService.get.and.callFake((url: string): any => {
      if (url === '/admin/users') return of(users);
      if (url.includes('/governance/users/')) return of([...permissions]);
      return of([]);
    });

    const fixture = TestBed.createComponent(GovernanceComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.onUserSelect('u1');
    tick();

    comp.grantForm.get('fieldName')!.setValue('date_of_birth');
    comp.grantForm.get('classification')!.setValue('CONFIDENTIAL');
    comp.grantPermission();
    tick();

    expect(apiService.put).toHaveBeenCalledWith('/governance/users/u1/permissions', {
      fieldName: 'date_of_birth',
      classification: 'CONFIDENTIAL',
    });
    expect(comp.permissions).toEqual(permissions);
    expect(comp.grantingPerm).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith('Permission granted.', 'Dismiss', jasmine.any(Object));
  }));

  it('grantPermission() shows error snack on failure', fakeAsync(() => {
    apiService.put.and.returnValue(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(GovernanceComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.selectedUserId = 'u1';
    comp.grantForm.get('fieldName')!.setValue('ssn');
    comp.grantPermission();
    tick();

    expect(snackBar.open).toHaveBeenCalledWith('Failed to grant permission.', 'Dismiss', jasmine.any(Object));
    expect(comp.grantingPerm).toBeFalse();
  }));

  it('getClassBadge() returns correct CSS class name', () => {
    const fixture = TestBed.createComponent(GovernanceComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    expect(comp.getClassBadge('PII')).toBe('class-pii');
    expect(comp.getClassBadge('CONFIDENTIAL')).toBe('class-confidential');
    expect(comp.getClassBadge('PUBLIC')).toBe('class-public');
    expect(comp.getClassBadge('RESTRICTED')).toBe('class-restricted');
  });

  it('sets errorMessage when users load fails', fakeAsync(() => {
    apiService.get.and.returnValue(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(GovernanceComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.errorMessage).toBe('Failed to load users.');
  }));
});
