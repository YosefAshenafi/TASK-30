import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminBackupsComponent } from '../../web/src/app/admin/pages/backups.component';

describe('AdminBackupsComponent', () => {
  let fixture: ComponentFixture<AdminBackupsComponent>;
  let component: AdminBackupsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminBackupsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminBackupsComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Backups heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Backups');
  });

  it('responds to user interaction: triggerBackup fires POST with mode', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));

    component.triggerBackup('FULL');
    const req = httpMock.expectOne(r => r.url.includes('/api/v1/admin/backups/run') && r.url.includes('mode=FULL'));
    req.flush({ id: 'b-1', type: 'FULL', status: 'RUNNING', sizeBytes: null, startedAt: '', completedAt: null, errorMessage: null });

    // After successful trigger, the component calls loadBackups which does another GET
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });

  it('handles empty state: shows "No backup runs yet"', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
    component.loading = false;
    component.backupRuns = [];
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No backup runs yet');
  });

  it('renders format sizes and status classes correctly', () => {
    expect(component.formatSize(512)).toBe('512 B');
    expect(component.formatSize(2048)).toContain('KB');
    expect(component.statusClass('COMPLETED')).toContain('green');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
