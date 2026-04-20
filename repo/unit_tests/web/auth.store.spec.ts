import { TestBed } from '@angular/core/testing';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';
import { UserProfile } from '../../web/src/app/core/models/user.model';

const mockProfile: UserProfile = {
  id: '550e8400-e29b-41d4-a716-446655440000',
  username: 'testuser',
  displayName: 'Test User',
  role: 'STUDENT',
  status: 'ACTIVE',
  organizationId: null,
  allowedIpRanges: [],
  lastLoginAt: null,
  createdAt: '2026-01-01T00:00:00Z',
};

describe('AuthStore', () => {
  let store: AuthStore;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    store = TestBed.inject(AuthStore);
  });

  afterEach(() => localStorage.clear());

  it('should not be authenticated initially', () => {
    expect(store.isAuthenticated()).toBeFalse();
    expect(store.userRole()).toBeNull();
    expect(store.userId()).toBeNull();
  });

  it('should set profile and mark authenticated', () => {
    store.setProfile(mockProfile);
    expect(store.isAuthenticated()).toBeTrue();
    expect(store.userRole()).toBe('STUDENT');
    expect(store.userId()).toBe(mockProfile.id);
  });

  it('should persist profile to localStorage', () => {
    store.setProfile(mockProfile);
    const stored = localStorage.getItem('meridian_profile');
    expect(stored).toBeTruthy();
    const parsed = JSON.parse(stored!);
    expect(parsed.id).toBe(mockProfile.id);
  });

  it('should hydrate profile from localStorage', () => {
    localStorage.setItem('meridian_profile', JSON.stringify(mockProfile));
    const newStore = TestBed.inject(AuthStore);
    newStore.hydrateFromStorage();
    expect(newStore.isAuthenticated()).toBeTrue();
    expect(newStore.userId()).toBe(mockProfile.id);
  });

  it('should clear profile on logout', () => {
    store.setProfile(mockProfile);
    store.clearProfile();
    expect(store.isAuthenticated()).toBeFalse();
    expect(localStorage.getItem('meridian_profile')).toBeNull();
  });

  it('should return organizationId for mentor', () => {
    const mentor: UserProfile = { ...mockProfile, role: 'CORPORATE_MENTOR', organizationId: 'org-1' };
    store.setProfile(mentor);
    expect(store.organizationId()).toBe('org-1');
  });

  it('should emit profile via observable', (done) => {
    store.profile$.subscribe(p => {
      if (p !== null) {
        expect(p.username).toBe('testuser');
        done();
      }
    });
    store.setProfile(mockProfile);
  });

  it('should handle invalid localStorage JSON gracefully', () => {
    localStorage.setItem('meridian_profile', '{invalid json');
    expect(() => store.hydrateFromStorage()).not.toThrow();
    expect(store.isAuthenticated()).toBeFalse();
  });

  it('should return userStatus when profile is set', () => {
    expect(store.userStatus()).toBeNull();
    store.setProfile(mockProfile);
    expect(store.userStatus()).toBe('ACTIVE');
  });

  it('should store tokens and profile on login', () => {
    store.login('access-token-123', 'refresh-token-456', mockProfile);
    expect(store.isAuthenticated()).toBeTrue();
    expect(store.userId()).toBe(mockProfile.id);
    expect(localStorage.getItem('meridian_access_token')).toBe('access-token-123');
    expect(localStorage.getItem('meridian_refresh_token')).toBe('refresh-token-456');
  });
});
