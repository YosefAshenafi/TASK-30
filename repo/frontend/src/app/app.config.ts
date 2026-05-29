import { ApplicationConfig, APP_INITIALIZER } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';

function initializeAuth(_authService: AuthService): () => Promise<void> {
  // Injecting AuthService here ensures its constructor runs at bootstrap, restoring any persisted
  // session from localStorage. We intentionally do NOT attempt a cookie-based refresh on startup:
  // when the refresh cookie is unavailable it 401s, and refresh() then clears the session and
  // redirects to /login — which would log out a returning user on reload and break public pages
  // such as /register. Token renewal is handled lazily by the HTTP interceptor on a 401.
  return () => Promise.resolve();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimations(),
    {
      provide: APP_INITIALIZER,
      useFactory: initializeAuth,
      deps: [AuthService],
      multi: true,
    },
  ],
};
