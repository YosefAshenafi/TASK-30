import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { WeakKnowledgePointsComponent } from '../../web/src/app/analytics/pages/weak-knowledge-points.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';

class FakeAuthStore {
  userRole(): string | null { return 'STUDENT'; }
  userId(): string | null { return 'user-1'; }
}

describe('WeakKnowledgePointsComponent', () => {
  let fixture: ComponentFixture<WeakKnowledgePointsComponent>;
  let component: WeakKnowledgePointsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WeakKnowledgePointsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: new FakeAuthStore() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WeakKnowledgePointsComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/weak-knowledge-points')).flush({ items: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Weak Knowledge Points');
  });

  it('responds to user interaction: STUDENT role disables learner filter', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/weak-knowledge-points')).flush({ items: [] });
    expect(component.canFilter).toBeFalse();
  });

  it('handles empty state: shows "No weak knowledge points"', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/weak-knowledge-points')).flush({ items: [] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No weak knowledge points');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ items: [] }));
  });
});
