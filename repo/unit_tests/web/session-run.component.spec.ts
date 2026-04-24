import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { BehaviorSubject, of } from 'rxjs';
import { SessionRunComponent } from '../../web/src/app/sessions/pages/session-run.component';
import { NetworkStatusService } from '../../web/src/app/core/stores/network-status.service';
import { SessionStore } from '../../web/src/app/sessions/session.store';

class FakeNetwork {
  online$ = new BehaviorSubject<boolean>(true);
  isOnline(): boolean { return true; }
}

class FakeSessionStore {
  upsertSession = jasmine.createSpy('upsertSession').and.returnValue(Promise.resolve());
  upsertSet = jasmine.createSpy('upsertSet').and.returnValue(Promise.resolve());
  getSetsForSession = jasmine.createSpy('getSetsForSession').and.returnValue(Promise.resolve([]));
}

describe('SessionRunComponent', () => {
  let fixture: ComponentFixture<SessionRunComponent>;
  let component: SessionRunComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SessionRunComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: NetworkStatusService, useValue: new FakeNetwork() },
        { provide: SessionStore, useValue: new FakeSessionStore() },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: (_k: string) => 'sess-1' } }, params: of({ id: 'sess-1' }) },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SessionRunComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state showing Loading when session not yet loaded', () => {
    component.session = null;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Loading session');
  });

  it('responds to user interaction: formatElapsed formats seconds correctly', () => {
    expect(component.formatElapsed(65)).toBe('01:05');
    expect(component.formatElapsed(3665)).toBe('1:01:05');
  });

  it('handles activity name lookup: returns fallback when activity not in list', () => {
    component.activities = [{ id: 'a-1', name: 'Squat' }];
    expect(component.activityName('a-1')).toBe('Squat');
    expect(component.activityName('')).toBe('Unassigned activity');
  });

  afterEach(() => {
    component.ngOnDestroy();
    const httpMock = TestBed.inject(HttpTestingController);
    // Flush any pending request opened by ngOnInit's loadSession, if triggered
    const pending = httpMock.match(() => true);
    pending.forEach(r => r.flush(null));
  });
});
