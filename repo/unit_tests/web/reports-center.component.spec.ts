import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ReportsCenterComponent } from '../../web/src/app/reports/pages/reports-center.component';

describe('ReportsCenterComponent', () => {
  let fixture: ComponentFixture<ReportsCenterComponent>;
  let component: ReportsCenterComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ReportsCenterComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ReportsCenterComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Reports Center heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/reports')).flush({ content: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Reports Center');
    expect(el.textContent).toContain('Run a Report');
  });

  it('responds to user interaction: setTab updates activeTab', () => {
    component.setTab('SUCCEEDED');
    expect(component.activeTab).toBe('SUCCEEDED');
  });

  it('handles filtering: filteredRuns filters by status', () => {
    component.runs = [
      { id: '1', kind: 'ENROLLMENTS', status: 'SUCCEEDED', outputPath: '/x', createdAt: '', completedAt: '', requestedBy: 'u' },
      { id: '2', kind: 'ENROLLMENTS', status: 'FAILED', outputPath: null, createdAt: '', completedAt: null, requestedBy: 'u' },
    ];
    component.activeTab = 'all';
    expect(component.filteredRuns().length).toBe(2);
    component.activeTab = 'SUCCEEDED';
    expect(component.filteredRuns().length).toBe(1);
  });

  it('renders status class map and download url correctly', () => {
    expect(component.statusClass('SUCCEEDED')).toContain('green');
    expect(component.downloadUrl('abc')).toContain('/api/v1/reports/abc/download');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
