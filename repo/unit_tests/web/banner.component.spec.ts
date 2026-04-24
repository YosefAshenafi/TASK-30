import { TestBed, ComponentFixture } from '@angular/core/testing';
import { BannerComponent } from '../../web/src/app/shared/ui/banner.component';

describe('BannerComponent', () => {
  let fixture: ComponentFixture<BannerComponent>;
  let component: BannerComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [BannerComponent] }).compileComponents();
    fixture = TestBed.createComponent(BannerComponent);
    component = fixture.componentInstance;
  });

  it('renders the input message text', () => {
    component.message = 'Offline — changes will sync when online';
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Offline — changes will sync when online');
  });

  it('uses the info severity class by default', () => {
    component.message = 'hello';
    fixture.detectChanges();
    const host = fixture.nativeElement.querySelector('[role="alert"]');
    expect(host.className).toContain('bg-sky-50');
  });

  it('switches to error classes when severity=error', () => {
    component.message = 'boom';
    component.severity = 'error';
    fixture.detectChanges();
    const host = fixture.nativeElement.querySelector('[role="alert"]');
    expect(host.className).toContain('bg-red-50');
    expect(host.className).toContain('text-red-800');
  });

  it('hides dismiss button by default', () => {
    component.message = 'msg';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('emits (dismissed) when dismissible and user clicks ×', () => {
    component.message = 'msg';
    component.dismissible = true;
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(btn).not.toBeNull();

    let emitted = 0;
    component.dismissed.subscribe(() => emitted++);
    btn.click();

    expect(emitted).toBe(1);
  });

  it('has role="alert" for screen-reader support', () => {
    component.message = 'msg';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
