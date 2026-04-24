import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { SessionsListComponent } from '../../web/src/app/sessions/pages/sessions-list.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';
import { NetworkStatusService } from '../../web/src/app/core/stores/network-status.service';
import { SessionStore } from '../../web/src/app/sessions/session.store';

class FakeAuthStore {
  userId(): string { return 'user-1'; }
}

class FakeNetwork {
  online$ = new BehaviorSubject<boolean>(true);
  isOnline(): boolean { return this.online$.getValue(); }
}

class FakeSessionStore {
  activeSessions$ = new BehaviorSubject<unknown[]>([]);
  loadActiveSessions = jasmine.createSpy('loadActiveSessions').and.returnValue(Promise.resolve());
}

describe('SessionsListComponent', () => {
  let fixture: ComponentFixture<SessionsListComponent>;
  let component: SessionsListComponent;
  let network: FakeNetwork;

  beforeEach(async () => {
    network = new FakeNetwork();
    await TestBed.configureTestingModule({
      imports: [SessionsListComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: new FakeAuthStore() },
        { provide: NetworkStatusService, useValue: network },
        { provide: SessionStore, useValue: new FakeSessionStore() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionsListComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with title and empty list', () => {
    component.loading = false;
    component.sessions = [];
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Training Sessions');
    expect(el.textContent).toContain('No sessions yet');
  });

  it('responds to user interaction: sortedSessions prioritises IN_PROGRESS first', () => {
    component.sessions = [
      { id: 'a', courseId: 'c', startedAt: '2024-01-01', status: 'COMPLETED', clientUpdatedAt: '' },
      { id: 'b', courseId: 'c', startedAt: '2024-01-01', status: 'IN_PROGRESS', clientUpdatedAt: '' },
    ];
    const sorted = component.sortedSessions();
    expect(sorted[0].status).toBe('IN_PROGRESS');
  });

  it('handles offline state: shows offline banner', () => {
    network.online$.next(false);
    component.loading = false;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Offline');
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());
});
