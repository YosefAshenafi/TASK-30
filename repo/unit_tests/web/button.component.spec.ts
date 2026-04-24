import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { ButtonComponent } from '../../web/src/app/shared/ui/button.component';

@Component({
  standalone: true,
  imports: [ButtonComponent],
  template: `
    <app-button [variant]="variant" [size]="size" [disabled]="disabled" [loading]="loading" [type]="type">
      {{ label }}
    </app-button>
  `,
})
class HostCmp {
  variant: 'primary' | 'secondary' | 'danger' | 'ghost' = 'primary';
  size: 'sm' | 'md' | 'lg' = 'md';
  disabled = false;
  loading = false;
  type: 'button' | 'submit' | 'reset' = 'button';
  label = 'Click me';
}

describe('ButtonComponent', () => {
  let fixture: ComponentFixture<HostCmp>;
  let host: HostCmp;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostCmp] }).compileComponents();
    fixture = TestBed.createComponent(HostCmp);
    host = fixture.componentInstance;
  });

  function btn(): HTMLButtonElement {
    return fixture.nativeElement.querySelector('button');
  }

  it('projects slotted label content', () => {
    fixture.detectChanges();
    expect(btn().textContent).toContain('Click me');
  });

  it('applies primary variant class by default and switches when input changes', () => {
    fixture.detectChanges();
    expect(btn().className).toContain('bg-[var(--color-brand-600)]');

    host.variant = 'danger';
    fixture.detectChanges();
    expect(btn().className).toContain('bg-[var(--color-danger)]');
  });

  it('disables the native button when [disabled] is true', () => {
    host.disabled = true;
    fixture.detectChanges();
    expect(btn().disabled).toBe(true);
  });

  it('disables the native button and shows a spinner while loading', () => {
    host.loading = true;
    fixture.detectChanges();
    expect(btn().disabled).toBe(true);
    expect(fixture.nativeElement.querySelector('.animate-spin')).not.toBeNull();
  });

  it('propagates the [type] input to the native button type attribute', () => {
    host.type = 'submit';
    fixture.detectChanges();
    expect(btn().type).toBe('submit');
  });

  it('switches size classes when size input changes', () => {
    host.size = 'lg';
    fixture.detectChanges();
    expect(btn().className).toContain('min-h-[52px]');

    host.size = 'sm';
    fixture.detectChanges();
    expect(btn().className).toContain('min-h-[36px]');
  });
});
