import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { HomeComponent } from '../home/home.component';
import { AuthStore } from '../core/stores/auth.store';
import { NetworkStatusService } from '../core/stores/network-status.service';

class FakeAuthStore {
  profile$ = new BehaviorSubject<{ displayName: string } | null>({ displayName: 'Test User' });
  userRole(): string | null { return 'STUDENT'; }
  userId(): string | null { return 'user-1'; }
}

class FakeNetworkStatus {
  online$ = new BehaviorSubject<boolean>(true);
  isOnline(): boolean { return this.online$.getValue(); }
}

// Flushes whatever ngOnInit fired so HttpClientTestingController.verify() stays clean.
function flushStartupRequests(httpMock: HttpTestingController): void {
  httpMock.match(req => req.url.startsWith('/api/v1/notifications/unread-count'))
    .forEach(r => r.flush({ unreadCount: 0 }));
  httpMock.match(req => req.url.startsWith('/api/v1/sessions'))
    .forEach(r => r.flush({ content: [], total: 0 }));
  httpMock.match(req => req.url.startsWith('/api/v1/admin/'))
    .forEach(r => r.flush({ total: 0 }));
}

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let component: HomeComponent;
  let network: FakeNetworkStatus;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    network = new FakeNetworkStatus();
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: new FakeAuthStore() },
        { provide: NetworkStatusService, useValue: network },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('renders initial state with greeting and role label', () => {
    fixture.detectChanges();
    flushStartupRequests(httpMock);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Welcome');
    expect(component.role).toBe('STUDENT');
    expect(component.roleLabel()).toBe('Learner Dashboard');
  });

  it('responds to user interaction: shows student widgets for STUDENT role', () => {
    fixture.detectChanges();
    flushStartupRequests(httpMock);
    component.loading = false;
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Active Sessions');
  });

  it('handles offline state: shows offline banner when network is down', () => {
    network.online$.next(false);
    fixture.detectChanges();
    flushStartupRequests(httpMock);
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('offline');
  });

  afterEach(() => httpMock.verify());
});
