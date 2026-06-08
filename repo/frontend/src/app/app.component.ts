import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule, NavigationEnd } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { Observable, filter, map } from 'rxjs';
import { AuthService, UserInfo } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatDividerModule,
  ],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss'],
})
export class AppComponent implements OnInit {
  currentUser$: Observable<UserInfo | null>;
  isMenuVisible = false;

  constructor(private authService: AuthService, private router: Router) {
    this.currentUser$ = this.authService.currentUser$;
  }

  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        map((e) => e as NavigationEnd)
      )
      .subscribe((event) => {
        const url = event.urlAfterRedirects;
        this.isMenuVisible = !['/login', '/register'].some((p) =>
          url.startsWith(p)
        );
      });
  }

  isStudent(): boolean {
    return this.authService.hasRole('STUDENT');
  }

  isMentorOrAdmin(): boolean {
    return this.authService.hasRole(
      'FACULTY_MENTOR',
      'CORPORATE_MENTOR',
      'ADMINISTRATOR'
    );
  }

  isAdmin(): boolean {
    return this.authService.hasRole('ADMINISTRATOR');
  }

  isFacultyOrAdmin(): boolean {
    return this.authService.hasRole('FACULTY_MENTOR', 'ADMINISTRATOR');
  }

  logout(): void {
    this.authService.logout().subscribe();
  }
}
