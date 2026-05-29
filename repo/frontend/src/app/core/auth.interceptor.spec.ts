import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService, AuthResponse } from './auth.service';
import { environment } from '../../environments/environment';
import { of } from 'rxjs';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  const testUrl = `${environment.apiUrl}/api/courses`;
  const loginUrl = `${environment.apiUrl}/api/auth/login`;

  const fakeAuthResponse: AuthResponse = {
    accessToken: 'new-access-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
    user: { id: 'u1', username: 'admin', role: 'ROLE_ADMINISTRATOR', organizationId: null },
  };

  beforeEach(() => {
    authService = jasmine.createSpyObj('AuthService', ['getAccessToken', 'refresh', 'logout']);
    router = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });

    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('adds Authorization header when token is present', fakeAsync(() => {
    authService.getAccessToken.and.returnValue('my-token');
    http.get(testUrl).subscribe();
    const req = httpMock.expectOne(testUrl);
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-token');
    req.flush({});
  }));

  it('does not add Authorization header when token is null', fakeAsync(() => {
    authService.getAccessToken.and.returnValue(null);
    http.get(testUrl).subscribe();
    const req = httpMock.expectOne(testUrl);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  }));

  it('does not add Authorization header for login URL', fakeAsync(() => {
    authService.getAccessToken.and.returnValue('my-token');
    http.post(loginUrl, {}).subscribe();
    const req = httpMock.expectOne(loginUrl);
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  }));

  it('retries with new token after 401 using refresh', fakeAsync(() => {
    authService.getAccessToken.and.returnValue('expired-token');
    authService.refresh.and.returnValue(of(fakeAuthResponse));

    http.get(testUrl).subscribe();

    const firstReq = httpMock.expectOne(testUrl);
    firstReq.flush({ message: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });
    tick();

    const retryReq = httpMock.expectOne(testUrl);
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    retryReq.flush({ data: 'ok' });
    tick();
  }));
});
