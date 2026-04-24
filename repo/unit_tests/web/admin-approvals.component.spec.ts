import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminApprovalsComponent } from '../../web/src/app/admin/pages/approvals.component';

describe('AdminApprovalsComponent', () => {
  let fixture: ComponentFixture<AdminApprovalsComponent>;
  let component: AdminApprovalsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminApprovalsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminApprovalsComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Pending Approvals heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/approvals')).flush({ content: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Pending Approvals');
  });

  it('responds to user interaction: decide() posts to approve endpoint', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/approvals')).flush({ content: [] });

    const item = { id: 'a-1', type: 'REPORT', status: 'PENDING', requestedBy: 'u', reviewedBy: null, reason: null, createdAt: '', decidedAt: null, expiresAt: '' };
    component.decide(item, 'APPROVED');
    httpMock.expectOne(r => r.url.endsWith('/approve') && r.method === 'POST').flush({ ok: true });
  });

  it('handles empty state: renders "No pending approvals" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/approvals')).flush({ content: [] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No pending approvals');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
