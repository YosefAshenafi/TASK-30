import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { HomeComponent } from '../../web/src/app/home/home.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';
import { NetworkStatusService } from '../../web/src/app/core/stores/network-status.service';

class FakeAuthStore {
  profile$ = new BehaviorSubject<{ displayName: string } | null>({ displayName: 'Test User' });
  userRole(): string | null { return 'STUDENT'; }
  userId(): string | null { return 'user-1'; }
}

class FakeNetworkStatus {
  online$ = new BehaviorSubject<boolean>(true);
  isOnline(): boolean { return this.online$.getValue(); }
}

describe('HomeComponent', () => {
  let fixture: ComponentFixture<HomeComponent>;
  let component: HomeComponent;
  let network: FakeNetworkStatus;

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
  });

  it('renders initial state with greeting and role label', () => {
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Welcome');
    expect(component.role).toBe('STUDENT');
    expect(component.roleLabel()).toBe('Learner Dashboard');
  });

  it('responds to user interaction: shows student widgets for STUDENT role', () => {
    fixture.detectChanges();
    component.loading = false;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Active Sessions');
  });

  it('handles offline state: shows offline banner when network is down', () => {
    network.online$.next(false);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('offline');
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());
});
