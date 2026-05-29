import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap, catchError, throwError, EMPTY } from 'rxjs';
import { environment } from '../../environments/environment';

export interface AuthUserInfo {
  id: string;
  username: string;
  role: string;
  organizationId: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthUserInfo;
}

export interface UserInfo {
  userId: string;
  username: string;
  role: string;
  organizationId: string | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  // Persisted so a full page reload (or the SSE client, which reads TOKEN_KEY) keeps the
  // session. Without this the in-memory token is lost on refresh and every guarded route
  // bounces back to /login.
  private static readonly TOKEN_KEY = 'meridian_token';
  private static readonly USER_KEY = 'meridian_user';

  private readonly baseUrl = environment.apiUrl;
  private accessToken: string | null = null;
  private userSubject = new BehaviorSubject<UserInfo | null>(null);

  currentUser$: Observable<UserInfo | null> = this.userSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {
    this.restoreSession();
  }

  private restoreSession(): void {
    try {
      const token = localStorage.getItem(AuthService.TOKEN_KEY);
      const userJson = localStorage.getItem(AuthService.USER_KEY);
      if (token && userJson) {
        this.accessToken = token;
        this.userSubject.next(JSON.parse(userJson) as UserInfo);
      }
    } catch {
      // Corrupt/unavailable storage — start unauthenticated.
    }
  }

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/api/auth/login`, { username, password }, { withCredentials: true })
      .pipe(
        tap((res) => this.setSession(res))
      );
  }

  register(username: string, password: string): Observable<any> {
    return this.http.post(`${this.baseUrl}/api/auth/register`, { username, password });
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.baseUrl}/api/auth/logout`, {}, { withCredentials: true })
      .pipe(
        tap(() => this.clearSession()),
        catchError(() => {
          this.clearSession();
          return EMPTY;
        })
      );
  }

  refresh(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${this.baseUrl}/api/auth/refresh`, {}, { withCredentials: true })
      .pipe(
        tap((res) => this.setSession(res)),
        catchError((err) => {
          this.clearSession();
          return throwError(() => err);
        })
      );
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  isAuthenticated(): boolean {
    return this.accessToken !== null && this.userSubject.value !== null;
  }

  getRole(): string | null {
    return this.userSubject.value?.role ?? null;
  }

  getUserId(): string | null {
    return this.userSubject.value?.userId ?? null;
  }

  getOrganizationId(): string | null {
    return this.userSubject.value?.organizationId ?? null;
  }

  hasRole(...roles: string[]): boolean {
    const currentRole = this.getRole();
    return currentRole !== null && roles.includes(currentRole);
  }

  private setSession(res: AuthResponse): void {
    this.accessToken = res.accessToken;
    const user: UserInfo = {
      userId: res.user.id,
      username: res.user.username,
      // Backend returns Spring authorities prefixed with ROLE_ (e.g. ROLE_ADMINISTRATOR);
      // client navigation and route guards compare against unprefixed role names.
      role: res.user.role?.replace(/^ROLE_/, '') ?? res.user.role,
      organizationId: res.user.organizationId,
    };
    this.userSubject.next(user);
    try {
      localStorage.setItem(AuthService.TOKEN_KEY, res.accessToken);
      localStorage.setItem(AuthService.USER_KEY, JSON.stringify(user));
    } catch {
      // Storage unavailable (e.g. private mode) — session stays in memory only.
    }
  }

  private clearSession(): void {
    this.accessToken = null;
    this.userSubject.next(null);
    try {
      localStorage.removeItem(AuthService.TOKEN_KEY);
      localStorage.removeItem(AuthService.USER_KEY);
    } catch {
      // ignore
    }
    this.router.navigate(['/login']);
  }
}
