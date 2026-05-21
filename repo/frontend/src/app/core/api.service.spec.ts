import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { environment } from '../../environments/environment';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;
  const base = `${environment.apiUrl}/api`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ApiService],
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('get() sends GET request to correct URL', fakeAsync(() => {
    service.get('/courses').subscribe();
    const req = httpMock.expectOne(`${base}/courses`);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  }));

  it('get() appends query params', fakeAsync(() => {
    service.get('/analytics/mastery', { days: 30, active: true }).subscribe();
    const req = httpMock.expectOne((r) => r.url === `${base}/analytics/mastery`);
    expect(req.request.params.get('days')).toBe('30');
    expect(req.request.params.get('active')).toBe('true');
    req.flush([]);
  }));

  it('post() sends POST request with body', fakeAsync(() => {
    const body = { username: 'admin', password: 'pass' };
    service.post('/auth/login', body).subscribe();
    const req = httpMock.expectOne(`${base}/auth/login`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ accessToken: 'token' });
  }));

  it('put() sends PUT request with body', fakeAsync(() => {
    const body = { title: 'New Title' };
    service.put('/courses/123', body).subscribe();
    const req = httpMock.expectOne(`${base}/courses/123`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({});
  }));

  it('patch() sends PATCH request with body', fakeAsync(() => {
    const body = { roleName: 'ROLE_FACULTY_MENTOR' };
    service.patch('/admin/users/456/role', body).subscribe();
    const req = httpMock.expectOne(`${base}/admin/users/456/role`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(body);
    req.flush({});
  }));

  it('delete() sends DELETE request', fakeAsync(() => {
    service.delete('/reports/schedules/789').subscribe();
    const req = httpMock.expectOne(`${base}/reports/schedules/789`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  }));
});
