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
  private readonly baseUrl = environment.apiUrl;
  private accessToken: string | null = null;
  private userSubject = new BehaviorSubject<UserInfo | null>(null);

  currentUser$: Observable<UserInfo | null> = this.userSubject.asObservable();

  constructor(private http: HttpClient, private router: Router) {}

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
    this.userSubject.next({
      userId: res.user.id,
      username: res.user.username,
      role: res.user.role,
      organizationId: res.user.organizationId,
    });
  }

  private clearSession(): void {
    this.accessToken = null;
    this.userSubject.next(null);
    this.router.navigate(['/login']);
  }
}
