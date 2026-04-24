import { TestBed } from '@angular/core/testing';
import { HttpRequest, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { offlineInterceptor } from '../../web/src/app/core/http/offline.interceptor';
import { NetworkStatusService } from '../../web/src/app/core/stores/network-status.service';
import { OutboxService } from '../../web/src/app/core/http/outbox.service';

describe('offlineInterceptor', () => {
  it('queues mutation and returns 202 when offline', done => {
    const enqueueSpy = jasmine.createSpy('enqueue');

    TestBed.configureTestingModule({
      providers: [
        { provide: NetworkStatusService, useValue: { isOnline: () => false } },
        { provide: OutboxService, useValue: { enqueue: enqueueSpy } },
      ],
    });

    const req = new HttpRequest('POST', '/api/v1/sessions/sync', { x: 1 }, {
      headers: { 'Idempotency-Key': 'offline-key' },
    });

    TestBed.runInInjectionContext(() =>
      offlineInterceptor(req, () => of(new HttpResponse({ status: 200 })))
    ).subscribe(resp => {
      expect(resp.status).toBe(202);
      expect(enqueueSpy).toHaveBeenCalled();
      done();
    });
  });

  it('passes through when online', done => {
    TestBed.configureTestingModule({
      providers: [
        { provide: NetworkStatusService, useValue: { isOnline: () => true } },
        { provide: OutboxService, useValue: { enqueue: () => {} } },
      ],
    });

    const req = new HttpRequest('POST', '/api/v1/sessions/sync', { x: 1 });

    TestBed.runInInjectionContext(() =>
      offlineInterceptor(req, () => of(new HttpResponse({ status: 200, body: { ok: true } })))
    ).subscribe(resp => {
      expect(resp.status).toBe(200);
      done();
    });
  });
});
