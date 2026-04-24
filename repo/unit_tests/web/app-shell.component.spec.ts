import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AppShellComponent } from '../../web/src/app/shared/ui/app-shell.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';
import { NetworkStatusService } from '../../web/src/app/core/stores/network-status.service';
import { BehaviorSubject } from 'rxjs';

class AuthStoreStub {
  private _role: 'STUDENT' | 'CORPORATE_MENTOR' | 'FACULTY_MENTOR' | 'ADMIN' | null = 'STUDENT';
  profile$ = new BehaviorSubject<any>({ username: 'alice' });
  userRole(): string | null { return this._role; }
  setRole(r: typeof this._role) { this._role = r; }
  refreshToken(): string | null { return 'rt-123'; }
  clearProfile = jasmine.createSpy('clearProfile');
}

class NetworkStatusStub {
  online$ = new BehaviorSubject<boolean>(true);
}

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;
  let component: AppShellComponent;
  let authStub: AuthStoreStub;
  let networkStub: NetworkStatusStub;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    authStub = new AuthStoreStub();
    networkStub = new NetworkStatusStub();
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: authStub },
        { provide: NetworkStatusService, useValue: networkStub },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AppShellComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.match(() => true).forEach(r => r.flush({}));
    httpMock.verify();
  });

  it('renders only nav items allowed by the user role', () => {
    authStub.setRole('STUDENT');
    fixture.detectChanges();
    const labels = Array.from(fixture.nativeElement.querySelectorAll('a'))
      .map((el: any) => el.textContent.trim());
    // Students see Home, Sessions, Notifications — not Admin/Analytics/Reports
    expect(labels.join('|')).toContain('Home');
    expect(labels.join('|')).toContain('Sessions');
    expect(labels.join('|')).toContain('Notifications');
    expect(labels.join('|')).not.toContain('Admin');
  });

  it('shows Admin navigation only when role is ADMIN', () => {
    authStub.setRole('ADMIN');
    fixture.detectChanges();
    const labels = Array.from(fixture.nativeElement.querySelectorAll('a'))
      .map((el: any) => el.textContent.trim());
    expect(labels.join('|')).toContain('Admin');
  });

  it('shows the Offline banner when network goes offline', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Offline');

    networkStub.online$.next(false);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Offline');
  });

  it('toggles the collapsed sidebar signal via header button', () => {
    fixture.detectChanges();
    expect(component.collapsed()).toBe(false);
    const toggle = fixture.nativeElement.querySelector('aside button');
    (toggle as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(component.collapsed()).toBe(true);
  });

  it('logout() POSTs to /api/v1/auth/logout, clears profile, and navigates to /login', () => {
    const router = TestBed.inject(Router);
    const nav = spyOn(router, 'navigate');
    fixture.detectChanges();

    component.logout();
    const req = httpMock.expectOne('/api/v1/auth/logout');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'rt-123' });
    req.flush(null);

    expect(authStub.clearProfile).toHaveBeenCalled();
    expect(nav).toHaveBeenCalledWith(['/login']);
  });

  it('logout() still clears profile and navigates even when the logout HTTP call fails', () => {
    const router = TestBed.inject(Router);
    const nav = spyOn(router, 'navigate');
    fixture.detectChanges();

    component.logout();
    const req = httpMock.expectOne('/api/v1/auth/logout');
    req.error(new ProgressEvent('network failure'));

    expect(authStub.clearProfile).toHaveBeenCalled();
    expect(nav).toHaveBeenCalledWith(['/login']);
  });
});
