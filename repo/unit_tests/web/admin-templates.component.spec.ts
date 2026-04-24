import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AdminTemplatesComponent } from '../../web/src/app/admin/pages/templates.component';

describe('AdminTemplatesComponent', () => {
  let fixture: ComponentFixture<AdminTemplatesComponent>;
  let component: AdminTemplatesComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminTemplatesComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminTemplatesComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Notification Templates heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/notification-templates')).flush({ content: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Notification Templates');
  });

  it('responds to user interaction: openEdit populates form and opens dialog', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/notification-templates')).flush({ content: [] });

    const tpl = { key: 'export.ready', subject: 'Ready', bodyMarkdown: '# body', variables: [], updatedAt: null };
    component.openEdit(tpl);
    expect(component.editOpen).toBeTrue();
    expect(component.editKey).toBe('export.ready');
    expect(component.editForm.value.subject).toBe('Ready');
  });

  it('handles form validation: saveEdit no-ops on invalid form', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/admin/notification-templates')).flush({ content: [] });

    component.editForm.setValue({ subject: '', bodyMarkdown: '' });
    component.saving = false;
    component.saveEdit();
    expect(component.saving).toBeFalse();
    httpMock.expectNone(r => r.method === 'PUT');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
