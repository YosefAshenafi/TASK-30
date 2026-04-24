import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminRecycleBinComponent } from '../../web/src/app/admin/pages/recycle-bin.component';

describe('AdminRecycleBinComponent', () => {
  let fixture: ComponentFixture<AdminRecycleBinComponent>;
  let component: AdminRecycleBinComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminRecycleBinComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminRecycleBinComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Recycle Bin heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Recycle Bin');
  });

  it('responds to user interaction: setTab switches tab and reloads', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));

    component.setTab('users');
    expect(component.activeTab).toBe('users');
    httpMock.match(r => r.url.includes('type=users')).forEach(r => r.flush({ content: [] }));
  });

  it('handles empty state: renders "Recycle bin is empty" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
    component.loading = false;
    component.entries = [];
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Recycle bin is empty');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
