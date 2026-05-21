import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-error',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="error-banner" *ngIf="message" role="alert">
      {{ message }}
    </div>
  `,
  styles: [
    `
      .error-banner {
        background: #f44336;
        color: white;
        padding: 8px 16px;
        border-radius: 4px;
        font-size: 14px;
        margin: 8px 0;
      }
    `,
  ],
})
export class ErrorDisplayComponent {
  @Input() message: string = '';
}
