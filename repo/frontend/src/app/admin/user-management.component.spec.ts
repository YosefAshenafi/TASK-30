import { TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';
import { UserManagementComponent } from './user-management.component';
import { ApiService } from '../core/api.service';

describe('UserManagementComponent', () => {
  let apiService: jasmine.SpyObj<ApiService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const pendingUsers = [
    { id: 'u1', username: 'bob', createdAt: '2025-01-01T00:00:00Z', requestedRole: 'STUDENT' },
  ];
  const allUsers = [
    { id: 'u2', username: 'alice', role: 'STUDENT', status: 'ACTIVE', createdAt: '2025-01-01T00:00:00Z' },
    { id: 'u3', username: 'carol', role: 'ADMINISTRATOR', status: 'ACTIVE', createdAt: '2025-01-02T00:00:00Z' },
  ];

  beforeEach(() => {
    apiService = jasmine.createSpyObj('ApiService', ['get', 'post', 'put', 'patch', 'delete']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    apiService.get.and.callFake((url: string): any => {
      if (url === '/admin/users/pending') return of(pendingUsers);
      if (url === '/admin/users') return of(allUsers);
      return of([]);
    });

    TestBed.configureTestingModule({
      imports: [UserManagementComponent],
      providers: [
        { provide: ApiService, useValue: apiService },
        { provide: MatSnackBar, useValue: snackBar },
        provideNoopAnimations(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    // MatSnackBar is providedIn 'root'; for this standalone component the module-level provider
    // above is shadowed by the component injector, so force the mock everywhere it is requested.
    TestBed.overrideProvider(MatSnackBar, { useValue: snackBar });
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(UserManagementComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads pending users and all users on init', fakeAsync(() => {
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.pendingUsers).toEqual(pendingUsers);
    expect(fixture.componentInstance.users).toEqual(allUsers);
  }));

  it('approveUser() POSTs to approve endpoint and removes user from pending list', fakeAsync(() => {
    apiService.put.and.returnValue(of({}));
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.approveUser(pendingUsers[0]);
    flush();

    expect(apiService.put).toHaveBeenCalledWith('/admin/users/u1/approve', {});
    expect(comp.pendingUsers.find((u) => u.id === 'u1')).toBeUndefined();
    expect(comp.processingUserId).toBeNull();
    expect(snackBar.open).toHaveBeenCalledWith('bob approved.', 'Dismiss', jasmine.any(Object));
  }));

  it('approveUser() shows error snack on failure', fakeAsync(() => {
    apiService.put.and.returnValue(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.approveUser(pendingUsers[0]);
    flush();

    expect(snackBar.open).toHaveBeenCalledWith('Failed to approve user.', 'Dismiss', jasmine.any(Object));
    expect(fixture.componentInstance.processingUserId).toBeNull();
  }));

  it('rejectUser() POSTs to reject endpoint and removes user from pending list', fakeAsync(() => {
    apiService.put.and.returnValue(of({}));
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.rejectUser(pendingUsers[0]);
    flush();

    expect(apiService.put).toHaveBeenCalledWith('/admin/users/u1/reject', {});
    expect(comp.pendingUsers.find((u) => u.id === 'u1')).toBeUndefined();
    expect(snackBar.open).toHaveBeenCalledWith('bob rejected.', 'Dismiss', jasmine.any(Object));
  }));

  it('changeRole() PATCHes role endpoint and updates user object', fakeAsync(() => {
    apiService.patch.and.returnValue(of({}));
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    const user = fixture.componentInstance.users[0];
    fixture.componentInstance.changeRole(user, 'FACULTY_MENTOR');
    flush();

    expect(apiService.patch).toHaveBeenCalledWith('/admin/users/u2/role', { roleName: 'FACULTY_MENTOR' });
    expect(user.role).toBe('FACULTY_MENTOR');
    expect(snackBar.open).toHaveBeenCalledWith(
      'Role updated to FACULTY_MENTOR.',
      'Dismiss',
      jasmine.any(Object)
    );
  }));

  it('changeRole() shows error snack on failure', fakeAsync(() => {
    apiService.patch.and.returnValue(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.changeRole(allUsers[0], 'CORPORATE_MENTOR');
    flush();

    expect(snackBar.open).toHaveBeenCalledWith('Failed to update role.', 'Dismiss', jasmine.any(Object));
  }));

  it('onRoleFilterChange() resets page index and updates paged users', fakeAsync(() => {
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.pageIndex = 2;
    comp.roleFilter = 'STUDENT';
    comp.onRoleFilterChange();

    expect(comp.pageIndex).toBe(0);
    expect(comp.pagedUsers.every((u) => u.role === 'STUDENT')).toBeTrue();
  }));

  it('sets errorMessage when pending users load fails', fakeAsync(() => {
    apiService.get.and.callFake((url: string): any => {
      if (url === '/admin/users/pending') return throwError(() => new Error('fail'));
      return of([]);
    });

    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.errorMessage).toBe('Failed to load pending users.');
  }));

  it('createUser() POSTs the new user and reloads the list', fakeAsync(() => {
    apiService.post.and.returnValue(of({ id: 'u9', username: 'newbie' }));
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.createForm.setValue({ username: 'newbie', password: 'Newbie@12345678', role: 'STUDENT' });
    comp.createUser();
    flush();

    expect(apiService.post).toHaveBeenCalledWith('/admin/users', {
      username: 'newbie',
      password: 'Newbie@12345678',
      role: 'STUDENT',
    });
    expect(comp.creatingUser).toBeFalse();
    expect(comp.showCreateForm).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith('User newbie created.', 'Dismiss', jasmine.any(Object));
  }));

  it('changeStatus() PATCHes the status endpoint and updates the user', fakeAsync(() => {
    apiService.patch.and.returnValue(of({}));
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    const user = fixture.componentInstance.users[0];
    fixture.componentInstance.changeStatus(user, 'LOCKED');
    flush();

    expect(apiService.patch).toHaveBeenCalledWith('/admin/users/u2/status', { status: 'LOCKED' });
    expect(user.status).toBe('LOCKED');
    expect(snackBar.open).toHaveBeenCalledWith('Status updated to LOCKED.', 'Dismiss', jasmine.any(Object));
  }));

  it('deleteUser() DELETEs after confirmation and removes the user', fakeAsync(() => {
    spyOn(window, 'confirm').and.returnValue(true);
    apiService.delete.and.returnValue(of(null));
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.deleteUser(allUsers[0]);
    flush();

    expect(apiService.delete).toHaveBeenCalledWith('/admin/users/u2');
    expect(comp.users.find((u) => u.id === 'u2')).toBeUndefined();
    expect(snackBar.open).toHaveBeenCalledWith('User alice deleted.', 'Dismiss', jasmine.any(Object));
  }));

  it('deleteUser() does nothing when confirmation is declined', fakeAsync(() => {
    spyOn(window, 'confirm').and.returnValue(false);
    const fixture = TestBed.createComponent(UserManagementComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.deleteUser(allUsers[0]);
    flush();

    expect(apiService.delete).not.toHaveBeenCalled();
  }));
});
