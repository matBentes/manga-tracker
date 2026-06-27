import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  username = '';
  password = '';
  isSubmitting = false;
  error: string | null = null;

  get isValid(): boolean {
    return this.username.trim().length > 0 && this.password.length > 0;
  }

  onSubmit(): void {
    if (!this.isValid || this.isSubmitting) {
      return;
    }
    this.isSubmitting = true;
    this.error = null;
    this.auth.login(this.username.trim(), this.password).subscribe({
      next: () => this.router.navigate(['']),
      error: (err: HttpErrorResponse) => {
        this.isSubmitting = false;
        this.error = err.status === 401 ? 'Invalid credentials' : 'Login failed. Please try again.';
      },
    });
  }
}
