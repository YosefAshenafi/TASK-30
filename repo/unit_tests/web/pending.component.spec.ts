import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { PendingComponent } from '../../web/src/app/auth/pages/pending.component';
import { AuthStore } from '../../web/src/app/core/stores/auth.store';

class FakeAuthStore {
  private authed = false;
  isAuthenticated(): boolean { return this.authed; }
  setAuthed(v: boolean): void { this.authed = v; }
  setProfile = jasmine.createSpy('setProfile');
  clearProfile = jasmine.createSpy('clearProfile');
}

describe('PendingComponent', () => {
  let fixture: ComponentFixture<PendingComponent>;
  let component: PendingComponent;
  let authStore: FakeAuthStore;

  beforeEach(async () => {
    authStore = new FakeAuthStore();
    await TestBed.configureTestingModule({
      imports: [PendingComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthStore, useValue: authStore },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PendingComponent);
    component = fixture.componentInstance;
  });

  it('renders initial state with pending approval message', () => {
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Pending Approval');
    expect(el.textContent).toContain('Back to sign in');
  });

  it('responds to user interaction: logout clears profile and navigates to /login', () => {
    const router = TestBed.inject(Router);
    const navSpy = spyOn(router, 'navigate');
    fixture.detectChanges();
    component.logout();
    expect(authStore.clearProfile).toHaveBeenCalled();
    expect(navSpy).toHaveBeenCalledWith(['/login']);
  });

  it('handles unauthenticated state: skips polling on ngOnInit', () => {
    authStore.setAuthed(false);
    fixture.detectChanges(); // triggers ngOnInit
    // When not authenticated, no poll subscription should be created
    expect((component as unknown as { pollSubscription?: unknown }).pollSubscription).toBeUndefined();
  });

  afterEach(() => TestBed.inject(HttpTestingController).verify());
});
