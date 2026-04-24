import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject } from 'rxjs';
import { ToastContainerComponent } from '../../web/src/app/shared/ui/toast-container.component';
import { ToastService, Toast } from '../../web/src/app/core/stores/toast.service';

class FakeToastService {
  toasts$ = new BehaviorSubject<Toast[]>([]);
  dismiss = jasmine.createSpy('dismiss');
}

describe('ToastContainerComponent', () => {
  let fixture: ComponentFixture<ToastContainerComponent>;
  let component: ToastContainerComponent;
  let toastService: FakeToastService;

  beforeEach(async () => {
    toastService = new FakeToastService();
    await TestBed.configureTestingModule({
      imports: [ToastContainerComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ToastService, useValue: toastService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ToastContainerComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state: empty container when no toasts', () => {
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('[class*="pointer-events-auto"]').length).toBe(0);
  });

  it('responds to state changes: renders toasts pushed to observable', () => {
    toastService.toasts$.next([
      { id: 1, message: 'Saved!', severity: 'success' },
      { id: 2, message: 'Failed', severity: 'error' },
    ]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Saved!');
    expect(el.textContent).toContain('Failed');
  });

  it('handles user interaction: clicking × calls dismiss with correct id', () => {
    toastService.toasts$.next([{ id: 42, message: 'Hi', severity: 'info' }]);
    fixture.detectChanges();
    const btn = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    btn.click();
    expect(toastService.dismiss).toHaveBeenCalledWith(42);
  });

  it('maps severity to colour classes correctly', () => {
    expect(component.toastClass({ id: 1, message: 'x', severity: 'success' })).toContain('green');
    expect(component.toastClass({ id: 1, message: 'x', severity: 'error' })).toContain('red');
    expect(component.toastClass({ id: 1, message: 'x', severity: 'warn' })).toContain('amber');
    expect(component.toastClass({ id: 1, message: 'x', severity: 'info' })).toContain('sky');
  });
});
