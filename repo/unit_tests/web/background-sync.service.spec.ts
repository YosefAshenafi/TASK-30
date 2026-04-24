import { Subject } from 'rxjs';
import { BackgroundSyncService } from '../../web/src/app/core/http/background-sync.service';

describe('BackgroundSyncService', () => {
  it('drains outbox when network transitions to online', async () => {
    const online$ = new Subject<boolean>();
    const network = { online$ };
    const outbox = { drainPending: jasmine.createSpy('drainPending').and.returnValue(Promise.resolve()) };

    const service = new BackgroundSyncService(
      network as never,
      outbox as never,
    );

    online$.next(false);
    online$.next(true);

    await Promise.resolve();
    expect(outbox.drainPending).toHaveBeenCalledTimes(1);

    service.ngOnDestroy();
  });
});
