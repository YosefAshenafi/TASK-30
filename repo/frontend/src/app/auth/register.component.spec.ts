import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { RegisterComponent } from './register.component';
import { AuthService } from '../core/auth.service';

describe('RegisterComponent', () => {
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['register']);

    TestBed.configureTestingModule({
      imports: [RegisterComponent, ReactiveFormsModule],
      providers: [
        { provide: AuthService, useValue: authService },
        provideRouter([]),
        provideNoopAnimations(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('form is invalid when empty', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.registerForm.valid).toBeFalse();
  });

  it('form is invalid when username too short', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.registerForm.get('username')!.setValue('ab');
    comp.registerForm.get('password')!.setValue('ValidPass1!23');
    comp.registerForm.get('passwordConfirm')!.setValue('ValidPass1!23');
    expect(comp.registerForm.get('username')!.hasError('minlength')).toBeTrue();
  });

  it('form is invalid when passwords do not match', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.registerForm.get('username')!.setValue('validuser');
    comp.registerForm.get('password')!.setValue('ValidPass1!23');
    comp.registerForm.get('passwordConfirm')!.setValue('DifferentPass1!');
    expect(comp.registerForm.hasError('passwordMismatch')).toBeTrue();
  });

  it('form is invalid when password lacks symbol', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.registerForm.get('password')!.setValue('NoSymbol1234567');
    expect(comp.registerForm.get('password')!.hasError('pattern')).toBeTrue();
  });

  it('onSubmit() marks all touched and does not call API when form invalid', () => {
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.onSubmit();
    expect(authService.register).not.toHaveBeenCalled();
    expect(comp.registerForm.get('username')!.touched).toBeTrue();
  });

  it('onSubmit() calls authService.register with username and password', fakeAsync(() => {
    authService.register.and.returnValue(of({}));
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    comp.registerForm.get('username')!.setValue('newuser');
    comp.registerForm.get('password')!.setValue('ValidPass1!23');
    comp.registerForm.get('passwordConfirm')!.setValue('ValidPass1!23');

    comp.onSubmit();
    tick();

    expect(authService.register).toHaveBeenCalledWith('newuser', 'ValidPass1!23');
    expect(comp.successMessage).toBe(
      'Registration successful. Awaiting administrator approval.'
    );
    expect(comp.loading).toBeFalse();
  }));

  it('onSubmit() sets errorMessage on 409 (username taken)', fakeAsync(() => {
    authService.register.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 409 }))
    );
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    comp.registerForm.get('username')!.setValue('takenuser');
    comp.registerForm.get('password')!.setValue('ValidPass1!23');
    comp.registerForm.get('passwordConfirm')!.setValue('ValidPass1!23');
    comp.onSubmit();
    tick();

    expect(comp.errorMessage).toBe('Username already taken.');
    expect(comp.loading).toBeFalse();
  }));

  it('onSubmit() sets generic errorMessage on other errors', fakeAsync(() => {
    authService.register.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 500 }))
    );
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    comp.registerForm.get('username')!.setValue('someuser');
    comp.registerForm.get('password')!.setValue('ValidPass1!23');
    comp.registerForm.get('passwordConfirm')!.setValue('ValidPass1!23');
    comp.onSubmit();
    tick();

    expect(comp.errorMessage).toBe('Registration failed. Please try again.');
    expect(comp.loading).toBeFalse();
  }));

  it('clears errorMessage on each new submit attempt', fakeAsync(() => {
    authService.register.and.returnValue(of({}));
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;
    comp.errorMessage = 'Previous error';

    comp.registerForm.get('username')!.setValue('newuser');
    comp.registerForm.get('password')!.setValue('ValidPass1!23');
    comp.registerForm.get('passwordConfirm')!.setValue('ValidPass1!23');
    comp.onSubmit();
    tick();

    expect(comp.errorMessage).toBe('');
  }));
});
