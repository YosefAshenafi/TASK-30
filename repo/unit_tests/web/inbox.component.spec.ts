import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { InboxComponent } from '../../web/src/app/notifications/pages/inbox.component';

describe('InboxComponent', () => {
  let fixture: ComponentFixture<InboxComponent>;
  let component: InboxComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InboxComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InboxComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with Notifications heading and tabs', () => {
    component.loading = false;
    component.notifications = [];
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Notifications');
    expect(el.textContent).toContain('No unread notifications');
  });

  it('responds to user interaction: setTab switches activeTab', () => {
    component.setTab('all');
    expect(component.activeTab).toBe('all');
    component.setTab('unread');
    expect(component.activeTab).toBe('unread');
  });

  it('handles empty state: filtered() returns only unread when tab is unread', () => {
    component.notifications = [
      { id: '1', templateKey: 'x', payload: '{}', severity: 'INFO', readAt: null, createdAt: '' },
      { id: '2', templateKey: 'x', payload: '{}', severity: 'INFO', readAt: new Date().toISOString(), createdAt: '' },
    ];
    component.activeTab = 'unread';
    expect(component.filtered().length).toBe(1);
    component.activeTab = 'all';
    expect(component.filtered().length).toBe(2);
  });

  it('renders helpful template label mapping', () => {
    expect(component.templateLabel('export.ready')).toBe('Export Ready');
    expect(component.templateLabel('unknown.key')).toBe('unknown.key');
  });

  afterEach(() => {
    component.ngOnDestroy();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ content: [] }));
  });
});
