import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { WrongAnswersComponent } from '../../web/src/app/analytics/pages/wrong-answers.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';

class FakeAuthStore {
  userRole(): string | null { return 'ADMIN'; }
  userId(): string | null { return 'user-1'; }
}

describe('WrongAnswersComponent', () => {
  let fixture: ComponentFixture<WrongAnswersComponent>;
  let component: WrongAnswersComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WrongAnswersComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: new FakeAuthStore() },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WrongAnswersComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Wrong Answers heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/wrong-answers')).flush({ items: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Wrong Answers');
  });

  it('responds to user interaction: canFilter is true for non-student role', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/wrong-answers')).flush({ items: [] });
    expect(component.canFilter).toBeTrue();
  });

  it('handles empty state: renders "No wrong answers" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/wrong-answers')).flush({ items: [] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No wrong answers');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ items: [] }));
  });
});
