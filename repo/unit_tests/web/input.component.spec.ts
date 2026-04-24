import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { InputComponent } from '../../web/src/app/shared/ui/input.component';

@Component({
  standalone: true,
  imports: [InputComponent, ReactiveFormsModule],
  template: `
    <app-input
      [label]="label"
      [type]="type"
      [placeholder]="placeholder"
      [hint]="hint"
      [error]="error"
      [formControl]="control">
    </app-input>
  `,
})
class HostCmp {
  control = new FormControl<string>('');
  label = '';
  type = 'text';
  placeholder = '';
  hint = '';
  error = '';
}

describe('InputComponent', () => {
  let fixture: ComponentFixture<HostCmp>;
  let host: HostCmp;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostCmp] }).compileComponents();
    fixture = TestBed.createComponent(HostCmp);
    host = fixture.componentInstance;
  });

  function input(): HTMLInputElement {
    return fixture.nativeElement.querySelector('input');
  }

  it('renders label when provided', () => {
    host.label = 'Username';
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Username');
  });

  it('does not render a label element when no label is provided', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('label')).toBeNull();
  });

  it('propagates typed text back to the bound FormControl', () => {
    fixture.detectChanges();
    const el = input();
    el.value = 'hello';
    el.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(host.control.value).toBe('hello');
  });

  it('writes FormControl updates back into the native input', () => {
    fixture.detectChanges();
    host.control.setValue('from-control');
    fixture.detectChanges();
    expect(input().value).toBe('from-control');
  });

  it('disables the native input when the FormControl is disabled', () => {
    fixture.detectChanges();
    host.control.disable();
    fixture.detectChanges();
    expect(input().disabled).toBe(true);
  });

  it('renders hint text when hint input is set', () => {
    host.hint = 'We never share this';
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('We never share this');
  });

  it('renders error text when error input is set', () => {
    host.error = 'Required';
    fixture.detectChanges();
    const error = Array.from(fixture.nativeElement.querySelectorAll('p'))
      .find((p: any) => p.textContent.includes('Required'));
    expect(error).toBeTruthy();
  });

  it('forwards [type] input to the native input type attribute', () => {
    host.type = 'password';
    fixture.detectChanges();
    expect(input().type).toBe('password');
  });
});
