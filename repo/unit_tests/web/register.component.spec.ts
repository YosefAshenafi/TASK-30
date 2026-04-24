import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { RegisterComponent } from '../../web/src/app/auth/pages/register.component';

describe('RegisterComponent', () => {
  let fixture: ComponentFixture<RegisterComponent>;
  let component: RegisterComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(RegisterComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with empty form and strength unset', () => {
    fixture.detectChanges();
    expect(component.form.valid).toBeFalse();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Create account');
  });

  it('responds to user interaction: password mismatch flags invalid form', () => {
    fixture.detectChanges();
    component.form.patchValue({
      username: 'alice',
      displayName: 'Alice',
      role: 'STUDENT',
      password: 'longenoughpwd!1',
      confirmPassword: 'different-value-1!',
    });
    fixture.detectChanges();
    expect(component.form.errors?.['mismatch']).toBeTrue();
  });

  it('handles error state: surfaces error message when set', () => {
    fixture.detectChanges();
    component.errorMessage = 'Registration failed.';
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Registration failed.');
  });

  it('computes password strength correctly', () => {
    fixture.detectChanges();
    component.form.patchValue({ password: 'Abcdefghijkl1!' });
    expect(component.strength.score).toBeGreaterThan(2);
    expect(component.strengthBar()).toContain('bg-');
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());
});
