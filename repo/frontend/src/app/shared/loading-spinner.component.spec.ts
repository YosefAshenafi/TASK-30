import { TestBed } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { LoadingSpinnerComponent } from './loading-spinner.component';

describe('LoadingSpinnerComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoadingSpinnerComponent],
      schemas: [NO_ERRORS_SCHEMA],
    });
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(LoadingSpinnerComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('default loading value is false', () => {
    const fixture = TestBed.createComponent(LoadingSpinnerComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.loading).toBeFalse();
  });

  it('does not render wrapper when loading is false', () => {
    const fixture = TestBed.createComponent(LoadingSpinnerComponent);
    fixture.componentInstance.loading = false;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.spinner-wrapper')).toBeNull();
  });

  it('renders spinner wrapper with role="status" when loading is true', () => {
    const fixture = TestBed.createComponent(LoadingSpinnerComponent);
    fixture.componentInstance.loading = true;
    fixture.detectChanges();
    const wrapper: HTMLElement | null =
      fixture.nativeElement.querySelector('[role="status"]');
    expect(wrapper).not.toBeNull();
  });

  it('removes spinner wrapper when loading switches from true to false', () => {
    const fixture = TestBed.createComponent(LoadingSpinnerComponent);
    fixture.componentInstance.loading = true;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.spinner-wrapper')).not.toBeNull();

    fixture.componentInstance.loading = false;
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.spinner-wrapper')).toBeNull();
  });

  it('shows accessible aria-label on the wrapper', () => {
    const fixture = TestBed.createComponent(LoadingSpinnerComponent);
    fixture.componentInstance.loading = true;
    fixture.detectChanges();
    const wrapper: HTMLElement | null =
      fixture.nativeElement.querySelector('[aria-label="Loading..."]');
    expect(wrapper).not.toBeNull();
  });
});
