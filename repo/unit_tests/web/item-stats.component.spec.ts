import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ItemStatsComponent } from '../../web/src/app/analytics/pages/item-stats.component';

describe('ItemStatsComponent', () => {
  let fixture: ComponentFixture<ItemStatsComponent>;
  let component: ItemStatsComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ItemStatsComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ItemStatsComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with heading', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/item-stats')).flush({ items: [] });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Assessment Item Statistics');
  });

  it('responds to user interaction: clicking Apply re-loads via load()', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/item-stats')).flush({ items: [] });

    component.filters.patchValue({ courseId: 'c-1' });
    component.load();
    const req2 = httpMock.expectOne(r => r.url.includes('courseId=c-1'));
    req2.flush({ items: [] });
    expect(component.loading).toBeFalse();
  });

  it('handles empty state: renders "No item statistics" message', () => {
    fixture.detectChanges();
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.expectOne(r => r.url.startsWith('/api/v1/analytics/item-stats')).flush({ items: [] });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('No item statistics');
  });

  afterEach(() => {
    const httpMock = TestBed.inject(HttpTestingController);
    httpMock.match(() => true).forEach(r => r.flush({ items: [] }));
  });
});
