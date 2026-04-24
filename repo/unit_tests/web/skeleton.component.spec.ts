import { TestBed, ComponentFixture } from '@angular/core/testing';
import { SkeletonComponent } from '../../web/src/app/shared/ui/skeleton.component';

describe('SkeletonComponent', () => {
  let fixture: ComponentFixture<SkeletonComponent>;
  let component: SkeletonComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [SkeletonComponent] }).compileComponents();
    fixture = TestBed.createComponent(SkeletonComponent);
    component = fixture.componentInstance;
  });

  function root(): HTMLElement {
    return fixture.nativeElement.querySelector('div');
  }

  it('applies the animate-pulse and default shape classes', () => {
    fixture.detectChanges();
    expect(root().className).toContain('animate-pulse');
    expect(root().className).toContain('rounded');
  });

  it('honors the [height] and [width] inputs as inline styles', () => {
    component.height = '3rem';
    component.width = '120px';
    fixture.detectChanges();
    expect(root().style.height).toBe('3rem');
    expect(root().style.width).toBe('120px');
  });

  it('appends the extraClass input to the rendered class list', () => {
    component.extraClass = 'mb-4 max-w-md';
    fixture.detectChanges();
    expect(root().className).toContain('mb-4');
    expect(root().className).toContain('max-w-md');
  });
});
