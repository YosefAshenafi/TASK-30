import { TestBed, ComponentFixture } from '@angular/core/testing';
import { TabsComponent, Tab } from '../../web/src/app/shared/ui/tabs.component';

describe('TabsComponent', () => {
  let fixture: ComponentFixture<TabsComponent>;
  let component: TabsComponent;

  const tabs: Tab[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'history', label: 'History', badge: 3 },
    { id: 'settings', label: 'Settings' },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TabsComponent] }).compileComponents();
    fixture = TestBed.createComponent(TabsComponent);
    component = fixture.componentInstance;
    component.tabs = tabs;
    component.active = 'overview';
  });

  function buttons(): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('button'));
  }

  it('renders one button per tab with the tab label', () => {
    fixture.detectChanges();
    const btns = buttons();
    expect(btns.length).toBe(3);
    expect(btns.map(b => b.textContent?.trim())).toEqual(
      jasmine.arrayContaining([jasmine.stringContaining('Overview')]));
  });

  it('marks the active tab with the brand border class', () => {
    fixture.detectChanges();
    const btns = buttons();
    expect(btns[0].className).toContain('border-[var(--color-brand-600)]');
    expect(btns[1].className).toContain('border-transparent');
  });

  it('updates active styling when [active] input changes', () => {
    fixture.detectChanges();
    component.active = 'settings';
    fixture.detectChanges();
    const btns = buttons();
    expect(btns[2].className).toContain('border-[var(--color-brand-600)]');
    expect(btns[0].className).toContain('border-transparent');
  });

  it('emits (activeChange) with the clicked tab id', () => {
    fixture.detectChanges();
    let emitted: string | undefined;
    component.activeChange.subscribe(v => emitted = v);
    buttons()[1].click();
    expect(emitted).toBe('history');
  });

  it('renders a badge when a tab has badge metadata', () => {
    fixture.detectChanges();
    const historyBtn = buttons()[1];
    expect(historyBtn.textContent).toContain('3');
  });

  it('omits badge markup for tabs without badges', () => {
    fixture.detectChanges();
    const overviewBtn = buttons()[0];
    expect(overviewBtn.querySelectorAll('span').length).toBe(0);
  });
});
