import { HttpRequest, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { idempotencyInterceptor } from '../../web/src/app/core/http/idempotency.interceptor';

describe('idempotencyInterceptor', () => {
  it('adds Idempotency-Key to mutation requests', () => {
    const req = new HttpRequest('POST', '/api/v1/sessions', { a: 1 });
    let seen: HttpRequest<unknown> | null = null;

    idempotencyInterceptor(req, nextReq => {
      seen = nextReq;
      return of(new HttpResponse({ status: 200 }));
    }).subscribe();

    expect(seen).not.toBeNull();
    expect(seen!.headers.has('Idempotency-Key')).toBeTrue();
  });

  it('preserves existing Idempotency-Key when already set', () => {
    const req = new HttpRequest('PATCH', '/api/v1/sessions/1', { a: 1 }, {
      headers: { 'Idempotency-Key': 'existing-key' },
    });
    let seen: HttpRequest<unknown> | null = null;

    idempotencyInterceptor(req, nextReq => {
      seen = nextReq;
      return of(new HttpResponse({ status: 200 }));
    }).subscribe();

    expect(seen!.headers.get('Idempotency-Key')).toBe('existing-key');
  });
});
