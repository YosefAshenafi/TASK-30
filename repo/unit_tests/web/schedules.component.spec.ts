import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { SchedulesComponent } from '../../web/src/app/reports/pages/schedules.component';

describe('SchedulesComponent', () => {
  let fixture: ComponentFixture<SchedulesComponent>;
  let component: SchedulesComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SchedulesComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SchedulesComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Report Schedules heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/reports/schedules')).flush({ content: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Report Schedules');
    expect(el.textContent).toContain('Add Schedule');
  });

  it('responds to user interaction: form is invalid with empty kind and format', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/reports/schedules')).flush({ content: [] });
    expect(component.form.invalid).toBeTrue();
    component.form.patchValue({ kind: 'ENROLLMENTS', format: 'CSV' });
    expect(component.form.valid).toBeTrue();
  });

  it('handles empty state: shows "No schedules configured" when list empty', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/reports/schedules')).flush({ content: [] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No schedules configured');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
