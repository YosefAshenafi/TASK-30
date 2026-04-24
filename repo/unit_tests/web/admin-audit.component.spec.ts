import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminAuditComponent } from '../../web/src/app/admin/pages/audit.component';

describe('AdminAuditComponent', () => {
  let fixture: ComponentFixture<AdminAuditComponent>;
  let component: AdminAuditComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminAuditComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminAuditComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Audit Log heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/audit')).flush({ content: [], totalElements: 0 });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Audit Log');
  });

  it('responds to user interaction: typing filter + load() adds query params', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/audit')).flush({ content: [], totalElements: 0 });

    component.filters.patchValue({ action: 'LOGIN' });
    component.load();
    const req = httpMock.expectOne(r => r.url.includes('action=LOGIN'));
    req.flush({ content: [], totalElements: 0 });
  });

  it('handles empty state: renders "No events found" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/audit')).flush({ content: [], totalElements: 0 });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No events found');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [], totalElements: 0 }));
  });
});
