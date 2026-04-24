import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { DialogComponent } from '../../web/src/app/shared/ui/dialog.component';

describe('DialogComponent', () => {
  let fixture: ComponentFixture<DialogComponent>;
  let component: DialogComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DialogComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DialogComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state: hidden when open=false', () => {
    component.open = false;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[role="dialog"]')).toBeNull();
  });

  it('responds to user interaction: shows dialog when open=true with title', () => {
    component.open = true;
    component.title = 'Confirm action';
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[role="dialog"]')).not.toBeNull();
    expect(el.textContent).toContain('Confirm action');
  });

  it('handles close: emits closed when × button clicked', () => {
    component.open = true;
    fixture.detectChanges();
    let emitted = false;
    component.closed.subscribe(() => (emitted = true));
    const btn = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    btn.click();
    expect(emitted).toBeTrue();
  });

  it('handles backdrop click: emits closed when backdrop clicked and closeOnBackdrop=true', () => {
    component.open = true;
    component.closeOnBackdrop = true;
    fixture.detectChanges();
    let emitted = false;
    component.closed.subscribe(() => (emitted = true));
    const backdrop = fixture.nativeElement.querySelector('.fixed') as HTMLElement;
    // Simulate a click where target === currentTarget (backdrop itself)
    const evt = new MouseEvent('click', { bubbles: true });
    Object.defineProperty(evt, 'target', { value: backdrop });
    Object.defineProperty(evt, 'currentTarget', { value: backdrop });
    component.onBackdrop(evt);
    expect(emitted).toBeTrue();
  });
});
