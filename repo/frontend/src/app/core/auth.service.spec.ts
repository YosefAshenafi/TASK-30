import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AuthService, AuthResponse } from './auth.service';
import { environment } from '../../environments/environment';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const nestedAuthResponse: AuthResponse = {
    accessToken: 'access-token-xyz',
    tokenType: 'Bearer',
    expiresIn: 3600,
    user: {
      id: 'user-uuid-001',
      username: 'admin',
      role: 'ROLE_ADMINISTRATOR',
      organizationId: null,
    },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule, RouterTestingModule],
      providers: [AuthService],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('setSession reads userId from nested user.id', fakeAsync(() => {
    service.login('admin', 'Admin@12345678').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/auth/login`);
    req.flush(nestedAuthResponse);
    tick();

    expect(service.getUserId()).toBe('user-uuid-001');
    expect(service.getRole()).toBe('ROLE_ADMINISTRATOR');
    expect(service.isAuthenticated()).toBeTrue();
  }));

  it('setSession reads username from nested user.username', fakeAsync(() => {
    service.login('admin', 'Admin@12345678').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/auth/login`);
    req.flush(nestedAuthResponse);
    tick();

    service.currentUser$.subscribe((user) => {
      if (user) {
        expect(user.username).toBe('admin');
      }
    });
  }));

  it('setSession reads organizationId from nested user.organizationId', fakeAsync(() => {
    const responseWithOrg: AuthResponse = {
      ...nestedAuthResponse,
      user: { ...nestedAuthResponse.user, organizationId: 'org-123' },
    };

    service.login('corp1', 'Corp@12345678').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/auth/login`);
    req.flush(responseWithOrg);
    tick();

    expect(service.getOrganizationId()).toBe('org-123');
  }));

  it('isAuthenticated returns false before login', () => {
    expect(service.isAuthenticated()).toBeFalse();
  });

  it('hasRole returns true for matching role', fakeAsync(() => {
    service.login('admin', 'Admin@12345678').subscribe();
    const req = httpMock.expectOne(`${environment.apiUrl}/api/auth/login`);
    req.flush(nestedAuthResponse);
    tick();

    expect(service.hasRole('ROLE_ADMINISTRATOR')).toBeTrue();
    expect(service.hasRole('ROLE_STUDENT')).toBeFalse();
  }));
});
