import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { SessionNewComponent } from '../../web/src/app/sessions/pages/session-new.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';
import { NetworkStatusService } from '../../web/src/app/core/stores/network-status.service';
import { SessionStore } from '../../web/src/app/sessions/session.store';

class FakeAuthStore {
  userId(): string { return 'user-1'; }
}

class FakeNetwork {
  online$ = new BehaviorSubject<boolean>(true);
  isOnline(): boolean { return true; }
}

class FakeSessionStore {
  upsertSession = jasmine.createSpy('upsertSession').and.returnValue(Promise.resolve());
}

describe('SessionNewComponent', () => {
  let fixture: ComponentFixture<SessionNewComponent>;
  let component: SessionNewComponent;
  let network: FakeNetwork;

  beforeEach(async () => {
    network = new FakeNetwork();
    await TestBed.configureTestingModule({
      imports: [SessionNewComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: new FakeAuthStore() },
        { provide: NetworkStatusService, useValue: network },
        { provide: SessionStore, useValue: new FakeSessionStore() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionNewComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with title and empty course options', () => {
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Start New Session');
    expect(component.form.invalid).toBeTrue();

    // Flush the initial GET /api/v1/courses request triggered by ngOnInit
    const httpMock = TestBed.inject(HttpTestingController);
    const req = httpMock.expectOne(r => r.url.startsWith('/api/v1/courses'));
    req.flush({ content: [] });
  });

  it('responds to user interaction: form becomes valid with required fields filled', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    // Initial GET /api/v1/courses?size=100 from ngOnInit
    httpMock.expectOne(r => r.method === 'GET' && r.url === '/api/v1/courses?size=100')
      .flush({ content: [] });

    component.form.patchValue({ courseId: 'c-1', restSecondsDefault: 60 });
    expect(component.form.get('courseId')?.valid).toBeTrue();

    // Selecting a courseId triggers a cohorts lookup; flush it so verify() stays clean.
    httpMock.expectOne('/api/v1/courses/c-1/cohorts').flush([]);
  });

  it('handles offline state: shows offline banner', () => {
    network.online$.next(false);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Offline');

    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/courses')).flush({ content: [] });
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());
});
