import { TestBed, fakeAsync, tick, flush } from '@angular/core/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';
import { BackupAdminComponent } from './backup-admin.component';
import { ApiService } from '../core/api.service';

describe('BackupAdminComponent', () => {
  let apiService: jasmine.SpyObj<ApiService>;
  let snackBar: jasmine.SpyObj<MatSnackBar>;

  const backupHistory = [
    {
      backupId: 'b1',
      type: 'FULL' as const,
      createdAt: '2025-01-01T00:00:00Z',
      sizeBytes: 1048576,
      retentionUntil: '2025-04-01T00:00:00Z',
      status: 'COMPLETED',
    },
  ];
  const recycleBin = [
    {
      recycleId: 'r1',
      entityType: 'Course',
      entityId: 'c1',
      deletedAt: '2025-01-05T00:00:00Z',
      expiresAt: '2025-04-05T00:00:00Z',
      deletedBy: 'admin',
    },
  ];

  beforeEach(() => {
    apiService = jasmine.createSpyObj('ApiService', ['get', 'post', 'delete']);
    snackBar = jasmine.createSpyObj('MatSnackBar', ['open']);

    apiService.get.and.callFake((url: string): any => {
      if (url === '/admin/backup/history') return of(backupHistory);
      if (url === '/admin/recycle-bin') return of(recycleBin);
      return of([]);
    });

    TestBed.configureTestingModule({
      imports: [BackupAdminComponent, ReactiveFormsModule],
      providers: [
        { provide: ApiService, useValue: apiService },
        { provide: MatSnackBar, useValue: snackBar },
        provideNoopAnimations(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
    });

    // Force the MatSnackBar mock (providedIn 'root' is otherwise shadowed by the component injector).
    TestBed.overrideProvider(MatSnackBar, { useValue: snackBar });
  });

  it('should be created', () => {
    const fixture = TestBed.createComponent(BackupAdminComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('loads backup history and recycle bin on init', fakeAsync(() => {
    const fixture = TestBed.createComponent(BackupAdminComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.backupHistory).toEqual(backupHistory);
    expect(fixture.componentInstance.recycleBin).toEqual(recycleBin);
  }));

  it('triggerBackup() POSTs with selected backup type and prepends record', fakeAsync(() => {
    const newBackup = { ...backupHistory[0], backupId: 'b2', type: 'INCREMENTAL' as const };
    apiService.post.and.returnValue(of(newBackup));

    const fixture = TestBed.createComponent(BackupAdminComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.backupForm.get('backupType')!.setValue('INCREMENTAL');
    comp.triggerBackup();
    flush();

    expect(apiService.post).toHaveBeenCalledWith('/admin/backup/trigger', { type: 'INCREMENTAL' });
    expect(comp.backupHistory[0]).toEqual(newBackup);
    expect(comp.triggeringBackup).toBeFalse();
    expect(snackBar.open).toHaveBeenCalledWith(
      jasmine.stringContaining('INCREMENTAL'),
      'Dismiss',
      jasmine.any(Object)
    );
  }));

  it('triggerBackup() shows error snack on failure', fakeAsync(() => {
    apiService.post.and.returnValue(throwError(() => new Error('fail')));
    const fixture = TestBed.createComponent(BackupAdminComponent);
    fixture.detectChanges();
    tick();

    fixture.componentInstance.triggerBackup();
    flush();

    expect(snackBar.open).toHaveBeenCalledWith('Failed to trigger backup.', 'Dismiss', jasmine.any(Object));
    expect(fixture.componentInstance.triggeringBackup).toBeFalse();
  }));

  it('restoreEntity() POSTs to restore and removes from recycle bin', fakeAsync(() => {
    apiService.post.and.returnValue(of({}));
    const fixture = TestBed.createComponent(BackupAdminComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.restoreEntity(recycleBin[0]);
    flush();

    expect(apiService.post).toHaveBeenCalledWith('/admin/recycle-bin/r1/restore', {});
    expect(comp.recycleBin.find((r) => r.recycleId === 'r1')).toBeUndefined();
    expect(comp.processingId).toBeNull();
    expect(snackBar.open).toHaveBeenCalledWith('Entity restored.', 'Dismiss', jasmine.any(Object));
  }));

  it('purgeEntity() DELETEs entity and removes from recycle bin', fakeAsync(() => {
    apiService.delete.and.returnValue(of(null));
    const fixture = TestBed.createComponent(BackupAdminComponent);
    fixture.detectChanges();
    tick();

    const comp = fixture.componentInstance;
    comp.purgeEntity(recycleBin[0]);
    flush();

    expect(apiService.delete).toHaveBeenCalledWith('/admin/recycle-bin/r1');
    expect(comp.recycleBin.find((r) => r.recycleId === 'r1')).toBeUndefined();
    expect(snackBar.open).toHaveBeenCalledWith('Entity permanently deleted.', 'Dismiss', jasmine.any(Object));
  }));

  it('formatBytes() returns human-readable size strings', () => {
    const fixture = TestBed.createComponent(BackupAdminComponent);
    fixture.detectChanges();
    const comp = fixture.componentInstance;

    expect(comp.formatBytes(512)).toBe('512 B');
    expect(comp.formatBytes(2048)).toBe('2.0 KB');
    expect(comp.formatBytes(1048576)).toBe('1.0 MB');
    expect(comp.formatBytes(1073741824)).toBe('1.00 GB');
  });

  it('sets errorMessage when backup history load fails', fakeAsync(() => {
    apiService.get.and.callFake((url: string): any => {
      if (url === '/admin/backup/history') return throwError(() => new Error('fail'));
      return of([]);
    });

    const fixture = TestBed.createComponent(BackupAdminComponent);
    fixture.detectChanges();
    tick();

    expect(fixture.componentInstance.errorMessage).toBe('Failed to load backup history.');
  }));
});
