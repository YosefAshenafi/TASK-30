import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminIpRangesComponent } from '../../web/src/app/admin/pages/ip-ranges.component';

describe('AdminIpRangesComponent', () => {
  let fixture: ComponentFixture<AdminIpRangesComponent>;
  let component: AdminIpRangesComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminIpRangesComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminIpRangesComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with IP Allow-list heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/allowed-ip-ranges')).flush([]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('IP Allow-list');
  });

  it('responds to user interaction: form becomes valid with CIDR filled', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/allowed-ip-ranges')).flush([]);

    expect(component.form.invalid).toBeTrue();
    component.form.patchValue({ cidr: '10.0.0.0/8' });
    expect(component.form.valid).toBeTrue();
  });

  it('handles empty state: renders "No IP rules configured" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/allowed-ip-ranges')).flush([]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No IP rules configured');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush([]));
  });
});
