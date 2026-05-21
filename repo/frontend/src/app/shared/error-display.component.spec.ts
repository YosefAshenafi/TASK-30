import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { ErrorDisplayComponent } from './error-display.component';

@Component({
  standalone: true,
  imports: [ErrorDisplayComponent],
  template: `<app-error [message]="msg"></app-error>`,
})
class HostComponent {
  msg = '';
}

describe('ErrorDisplayComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HostComponent, ErrorDisplayComponent],
    });
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(ErrorDisplayComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('does not render the banner when message is empty', () => {
    const fixture = TestBed.createComponent(ErrorDisplayComponent);
    fixture.componentInstance.message = '';
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.error-banner')).toBeNull();
  });

  it('renders the banner with message text when message is non-empty', () => {
    const fixture = TestBed.createComponent(ErrorDisplayComponent);
    fixture.componentInstance.message = 'Something went wrong.';
    fixture.detectChanges();
    const banner: HTMLElement | null = fixture.nativeElement.querySelector('[role="alert"]');
    expect(banner).not.toBeNull();
    expect(banner!.textContent?.trim()).toBe('Something went wrong.');
  });

  it('updates banner text when @Input message changes', () => {
    const fixture = TestBed.createComponent(ErrorDisplayComponent);
    fixture.componentInstance.message = 'First error';
    fixture.detectChanges();

    fixture.componentInstance.message = 'Updated error';
    fixture.detectChanges();

    const banner: HTMLElement | null = fixture.nativeElement.querySelector('[role="alert"]');
    expect(banner!.textContent?.trim()).toBe('Updated error');
  });

  it('hides banner when message is reset to empty string', () => {
    const fixture = TestBed.createComponent(ErrorDisplayComponent);
    fixture.componentInstance.message = 'Initial error';
    fixture.detectChanges();

    fixture.componentInstance.message = '';
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.error-banner')).toBeNull();
  });

  it('default message value is empty string', () => {
    const fixture = TestBed.createComponent(ErrorDisplayComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.message).toBe('');
  });
});
