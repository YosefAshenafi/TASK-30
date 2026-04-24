import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { throwError } from 'rxjs';
import { errorInterceptor } from '../../web/src/app/core/http/error.interceptor';
import { ToastService } from '../../web/src/app/core/stores/toast.service';

describe('errorInterceptor', () => {
  it('emits warning toast on 409 idempotency mismatch', done => {
    const toast = {
      error: jasmine.createSpy('error'),
      warn: jasmine.createSpy('warn'),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: ToastService, useValue: toast }],
    });

    const req = new HttpRequest('POST', '/api/v1/sessions/sync', {});
    const err = new HttpErrorResponse({
      status: 409,
      error: { error: { code: 'IDEMPOTENCY_MISMATCH', message: 'Conflict' } },
    });

    TestBed.runInInjectionContext(() =>
      errorInterceptor(req, () => throwError(() => err))
    ).subscribe({
      error: () => {
        expect(toast.warn).toHaveBeenCalled();
        expect(toast.error).not.toHaveBeenCalled();
        done();
      },
    });
  });

  it('emits error toast on 500', done => {
    const toast = {
      error: jasmine.createSpy('error'),
      warn: jasmine.createSpy('warn'),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: ToastService, useValue: toast }],
    });

    const req = new HttpRequest('GET', '/api/v1/reports', {});
    const err = new HttpErrorResponse({ status: 500, error: { error: { message: 'boom' } } });

    TestBed.runInInjectionContext(() =>
      errorInterceptor(req, () => throwError(() => err))
    ).subscribe({
      error: () => {
        expect(toast.error).toHaveBeenCalled();
        done();
      },
    });
  });
});
