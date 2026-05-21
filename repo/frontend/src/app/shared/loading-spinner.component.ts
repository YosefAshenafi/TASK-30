import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule],
  template: `
    <div class="spinner-wrapper" *ngIf="loading" role="status" aria-label="Loading...">
      <mat-spinner diameter="48"></mat-spinner>
    </div>
  `,
  styles: [
    `
      .spinner-wrapper {
        display: flex;
        justify-content: center;
        align-items: center;
        padding: 48px;
        width: 100%;
      }
    `,
  ],
})
export class LoadingSpinnerComponent {
  @Input() loading: boolean = false;
}
