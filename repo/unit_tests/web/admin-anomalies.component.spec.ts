import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminAnomaliesComponent } from '../../web/src/app/admin/pages/anomalies.component';

describe('AdminAnomaliesComponent', () => {
  let fixture: ComponentFixture<AdminAnomaliesComponent>;
  let component: AdminAnomaliesComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAnomaliesComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminAnomaliesComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Security Anomalies heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/anomalies')).flush({ content: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Security Anomalies');
  });

  it('responds to user interaction: resolve() POSTs to the resolve endpoint', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/anomalies')).flush({ content: [] });

    const item = { id: 'an-1', userId: 'u', type: 'LOGIN_BURST', ipAddress: '1.1.1.1', details: '', occurredAt: '', resolvedAt: null };
    component.resolve(item);
    httpMock.expectOne(r => r.url.endsWith('/resolve') && r.method === 'POST').flush({ ok: true });
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });

  it('handles empty state: renders "No unresolved anomalies" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/anomalies')).flush({ content: [] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No unresolved anomalies');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
