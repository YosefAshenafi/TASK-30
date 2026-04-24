import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { TableComponent, TableColumn } from '../../web/src/app/shared/ui/table.component';

interface Row extends Record<string, unknown> {
  id: string;
  name: string;
  status: string;
}

@Component({
  standalone: true,
  imports: [TableComponent],
  template: `
    <app-table
      [columns]="columns"
      [rows]="rows"
      [loading]="loading"
      [emptyMessage]="emptyMessage">
    </app-table>
  `,
})
class HostCmp {
  columns: TableColumn<Row>[] = [
    { key: 'name', label: 'Name' },
    { key: 'status', label: 'Status', render: (r: Row) => r.status.toUpperCase() },
  ];
  rows: Row[] = [];
  loading = false;
  emptyMessage = 'Nothing here yet';
}

describe('TableComponent', () => {
  let fixture: ComponentFixture<HostCmp>;
  let host: HostCmp;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HostCmp] }).compileComponents();
    fixture = TestBed.createComponent(HostCmp);
    host = fixture.componentInstance;
  });

  function ths(): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('thead th'))
      .map((th: any) => th.textContent.trim());
  }
  function bodyRows(): HTMLTableRowElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('tbody tr'));
  }

  it('renders one header per configured column', () => {
    fixture.detectChanges();
    expect(ths()).toEqual(['Name', 'Status']);
  });

  it('renders one row per data item with raw and rendered cells', () => {
    host.rows = [
      { id: '1', name: 'Alice', status: 'active' },
      { id: '2', name: 'Bob',   status: 'paused' },
    ];
    fixture.detectChanges();
    const rows = bodyRows();
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Alice');
    // `render` fn applied
    expect(rows[0].textContent).toContain('ACTIVE');
    expect(rows[1].textContent).toContain('PAUSED');
  });

  it('renders the empty message when rows are empty and not loading', () => {
    host.rows = [];
    host.loading = false;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nothing here yet');
  });

  it('hides the empty message and shows skeleton rows when loading', () => {
    host.rows = [];
    host.loading = true;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Nothing here yet');
    // 5 skeleton rows configured in component
    expect(bodyRows().length).toBe(5);
  });

  it('getCell returns empty string for null/undefined values', () => {
    const table = new (TableComponent as any)();
    expect(table.getCell({ x: null }, 'x')).toBe('');
    expect(table.getCell({}, 'x')).toBe('');
    expect(table.getCell({ x: 42 }, 'x')).toBe('42');
  });
});
