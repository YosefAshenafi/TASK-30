import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminUsersComponent } from '../../web/src/app/admin/pages/users.component';

describe('AdminUsersComponent', () => {
  let fixture: ComponentFixture<AdminUsersComponent>;
  let component: AdminUsersComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUsersComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUsersComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with User Management heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/users')).flush({ content: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('User Management');
  });

  it('responds to user interaction: setTab changes activeTab and reloads', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.includes('status=PENDING')).flush({ content: [] });

    component.setTab('ACTIVE');
    expect(component.activeTab).toBe('ACTIVE');
    httpMock.expectOne(r => r.url.includes('status=ACTIVE')).flush({ content: [] });
  });

  it('handles empty state: renders "No users found" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/users')).flush({ content: [] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No users found');
  });

  it('renders status class mapping correctly', () => {
    expect(component.statusClass('PENDING')).toContain('amber');
    expect(component.statusClass('ACTIVE')).toContain('green');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
