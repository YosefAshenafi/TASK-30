import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { MasteryTrendsComponent } from '../../web/src/app/analytics/pages/mastery-trends.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';

class FakeAuthStore {
  userRole(): string | null { return 'ADMIN'; }
  userId(): string | null { return 'user-1'; }
}

describe('MasteryTrendsComponent', () => {
  let fixture: ComponentFixture<MasteryTrendsComponent>;
  let component: MasteryTrendsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MasteryTrendsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: new FakeAuthStore() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MasteryTrendsComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Mastery Trends heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/mastery-trends')).flush({ scope: '', points: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Mastery Trends');
  });

  it('responds to user interaction: calling load() triggers another request', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/mastery-trends')).flush({ scope: '', points: [] });

    component.load();
    const req2 = httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/mastery-trends'));
    req2.flush({ scope: '', points: [] });
    expect(component.loading).toBeFalse();
  });

  it('handles empty state: shows "No mastery data" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/mastery-trends')).flush({ scope: '', points: [] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No mastery data');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ scope: '', points: [] }));
  });
});
