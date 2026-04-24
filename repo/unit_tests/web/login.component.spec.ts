import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { LoginComponent } from '../../web/src/app/auth/pages/login.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';

class FakeAuthStore {
  private authed = false;
  isAuthenticated(): boolean { return this.authed; }
  setAuthed(v: boolean): void { this.authed = v; }
  login = jasmine.createSpy('login');
}

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let component: LoginComponent;
  let authStore: FakeAuthStore;

  beforeEach(async () => {
    authStore = new FakeAuthStore();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: authStore },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with empty form', () => {
    fixture.detectChanges();
    expect(component.form.value.username).toBe('');
    expect(component.form.value.password).toBe('');
    expect(component.form.invalid).toBeTrue();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Sign in');
  });

  it('responds to user interaction: disables submit while form invalid, enables when filled', () => {
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(btn.disabled).toBeTrue();
    component.form.setValue({ username: 'user', password: 'secret' });
    fixture.detectChanges();
    expect(btn.disabled).toBeFalse();
  });

  it('handles already-authenticated state by redirecting on init', () => {
    authStore.setAuthed(true);
    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigate');
    fixture.detectChanges();
    expect(navSpy).toHaveBeenCalled();
  });

  it('handles error state: surfaces a message on invalid login', () => {
    fixture.detectChanges();
    component.errorMessage = 'Invalid username or password.';
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Invalid username or password.');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.verify();
  });
});
