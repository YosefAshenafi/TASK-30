import { ComponentFixture, TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { LoginComponent } from './login.component';
import { AuthService } from '../core/auth.service';
import { AuthResponse } from '../core/auth.service';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;
  let navigateSpy: jasmine.Spy;

  const mockAuthResponse: AuthResponse = {
    accessToken: 'test-token-abc',
    tokenType: 'Bearer',
    expiresIn: 3600,
    user: {
      id: 'user-1',
      username: 'admin',
      role: 'ROLE_ADMINISTRATOR',
      organizationId: null,
    },
  };

  beforeEach(async () => {
    authServiceSpy = jasmine.createSpyObj<AuthService>('AuthService', [
      'login',
      'register',
      'logout',
      'refresh',
      'isAuthenticated',
      'getRole',
      'getUserId',
      'hasRole',
      'getAccessToken',
    ]);
    await TestBed.configureTestingModule({
      imports: [
        LoginComponent,
        ReactiveFormsModule,
        HttpClientTestingModule,
        RouterTestingModule,
        NoopAnimationsModule,
      ],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // Use the real RouterTestingModule router (so RouterLink/ActivatedRoute work) but stub
    // navigation: a successful login calls router.navigate(['/dashboard']) and, with no routes
    // configured, a real navigation rejects with NG04002. Stubbing keeps tests deterministic.
    navigateSpy = spyOn(TestBed.inject(Router), 'navigate').and.returnValue(Promise.resolve(true));
  });

  it('renders login form', () => {
    expect(component).toBeTruthy();
    expect(component.loginForm.contains('username')).toBeTrue();
    expect(component.loginForm.contains('password')).toBeTrue();
  });

  it('shows required error on empty submit', () => {
    component.loginForm.get('username')!.setValue('');
    component.loginForm.get('password')!.setValue('');
    component.onSubmit();
    fixture.detectChanges();

    expect(component.loginForm.get('username')!.hasError('required')).toBeTrue();
    expect(component.loginForm.get('password')!.hasError('required')).toBeTrue();
    expect(component.loginForm.invalid).toBeTrue();
  });

  it('calls authService.login on valid submit', fakeAsync(() => {
    authServiceSpy.login.and.returnValue(of(mockAuthResponse));

    component.loginForm.setValue({ username: 'admin', password: 'Admin@12345678' });
    component.onSubmit();
    tick();

    expect(authServiceSpy.login).toHaveBeenCalledWith('admin', 'Admin@12345678');
  }));

  it('navigates to /dashboard on success', fakeAsync(() => {
    authServiceSpy.login.and.returnValue(of(mockAuthResponse));

    component.loginForm.setValue({ username: 'admin', password: 'Admin@12345678' });
    component.onSubmit();
    tick();

    expect(navigateSpy).toHaveBeenCalledWith(['/dashboard']);
  }));

  it('shows error message on 401', fakeAsync(() => {
    const errorResponse = new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' });
    authServiceSpy.login.and.returnValue(throwError(() => errorResponse));

    component.loginForm.setValue({ username: 'admin', password: 'wrongpassword' });
    component.onSubmit();
    tick();
    fixture.detectChanges();

    expect(component.errorMessage).toBe('Invalid username or password.');

    // Drain Material-scheduled timers left by the extra change-detection pass.
    flush();
  }));

  it('auth response has nested user contract with id, username, role, organizationId', () => {
    const response: AuthResponse = {
      accessToken: 'abc',
      tokenType: 'Bearer',
      expiresIn: 3600,
      user: {
        id: 'uid-123',
        username: 'testuser',
        role: 'ROLE_STUDENT',
        organizationId: 'org-456',
      },
    };

    expect(response.user.id).toBe('uid-123');
    expect(response.user.username).toBe('testuser');
    expect(response.user.role).toBe('ROLE_STUDENT');
    expect(response.user.organizationId).toBe('org-456');
  });
});
