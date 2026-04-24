import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpRequest, HttpResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import {
  authInterceptor,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  storeTokens,
} from '../../web/src/app/core/http/auth.interceptor';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';

describe('authInterceptor', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('stores and clears tokens via exported helpers', () => {
    storeTokens('a-token', 'r-token');
    expect(getAccessToken()).toBe('a-token');
    expect(getRefreshToken()).toBe('r-token');

    clearTokens();
    expect(getAccessToken()).toBeNull();
    expect(getRefreshToken()).toBeNull();
  });

  it('adds Authorization header for non-auth endpoint', done => {
    storeTokens('abc123', 'r1');

    const authStore = { clearProfile: jasmine.createSpy('clearProfile') };
    const router = { navigate: jasmine.createSpy('navigate') };
    const http = { post: jasmine.createSpy('post') };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthStore, useValue: authStore },
        { provide: Router, useValue: router },
        { provide: HttpClient, useValue: http },
      ],
    });

    const req = new HttpRequest('GET', '/api/v1/users/me');

    TestBed.runInInjectionContext(() =>
      authInterceptor(req, nextReq => {
        expect(nextReq.headers.get('Authorization')).toBe('Bearer abc123');
        return of(new HttpResponse({ status: 200 }));
      })
    ).subscribe(() => done());
  });

  it('does not add Authorization header for /auth/login', done => {
    storeTokens('abc123', 'r1');

    const authStore = { clearProfile: jasmine.createSpy('clearProfile') };
    const router = { navigate: jasmine.createSpy('navigate') };
    const http = { post: jasmine.createSpy('post') };

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthStore, useValue: authStore },
        { provide: Router, useValue: router },
        { provide: HttpClient, useValue: http },
      ],
    });

    const req = new HttpRequest('POST', '/api/v1/auth/login', { username: 'x', password: 'y' });

    TestBed.runInInjectionContext(() =>
      authInterceptor(req, nextReq => {
        expect(nextReq.headers.has('Authorization')).toBeFalse();
        return of(new HttpResponse({ status: 200 }));
      })
    ).subscribe(() => done());
  });
});
