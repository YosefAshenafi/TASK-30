import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ApiService } from '../../web/src/app/core/http/api.service';

describe('ApiService', () => {
  let service: ApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ApiService],
    });
    service = TestBed.inject(ApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('prefixes GET paths with /api/v1 and serializes params', () => {
    service.get('/reports', { page: 1, includeArchived: false }).subscribe();
    const req = httpMock.expectOne(r => r.url === '/api/v1/reports');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('includeArchived')).toBe('false');
    req.flush({});
  });

  it('adds Idempotency-Key header to POST mutations', () => {
    service.post('/sessions/sync', { sessions: [], sets: [] }).subscribe();
    const req = httpMock.expectOne('/api/v1/sessions/sync');
    expect(req.request.headers.has('Idempotency-Key')).toBeTrue();
    expect(req.request.headers.get('Content-Type')).toContain('application/json');
    req.flush({});
  });
});
