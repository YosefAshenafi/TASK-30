import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { SelectComponent, SelectOption } from '../../web/src/app/shared/ui/select.component';

@Component({
  standalone: true,
  imports: [SelectComponent, ReactiveFormsModule],
  template: `
    <app-select
      [label]="label"
      [placeholder]="placeholder"
      [options]="options"
      [formControl]="control">
    </app-select>
  `,
})
class HostCmp {
  control = new FormControl<string>('');
  label = '';
  placeholder = '';
  options: SelectOption[] = [
    { value: 'a', label: 'Apple' },
    { value: 'b', label: 'Banana' },
    { value: 'c', label: 'Cherry' },
  ];
}

describe('SelectComponent', () => {
  let fixture: ComponentFixture<HostCmp>;
  let host: HostCmp;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostCmp] }).compileComponents();
    fixture = TestBed.createComponent(HostCmp);
    host = fixture.componentInstance;
  });

  function select(): HTMLSelectElement {
    return fixture.nativeElement.querySelector('select');
  }

  it('renders one <option> per input option and a placeholder when provided', () => {
    host.placeholder = 'Choose…';
    fixture.detectChanges();
    const opts = select().querySelectorAll('option');
    // placeholder + 3 options
    expect(opts.length).toBe(4);
    expect(opts[0].textContent?.trim()).toBe('Choose…');
    expect(opts[1].textContent?.trim()).toBe('Apple');
  });

  it('updates the FormControl when the user picks an option', () => {
    fixture.detectChanges();
    const el = select();
    el.value = 'b';
    el.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(host.control.value).toBe('b');
  });

  it('disables the native select when the FormControl is disabled', () => {
    fixture.detectChanges();
    host.control.disable();
    fixture.detectChanges();
    expect(select().disabled).toBe(true);
  });

  it('renders the label element when label is set', () => {
    host.label = 'Fruit';
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('label')?.textContent).toContain('Fruit');
  });

  it('does not render a placeholder option when no placeholder provided', () => {
    fixture.detectChanges();
    const opts = select().querySelectorAll('option');
    expect(opts.length).toBe(3);
  });
});
