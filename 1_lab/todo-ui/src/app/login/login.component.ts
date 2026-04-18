import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  username = '';
  password = '';
  readonly errorMessage = signal<string | null>(null);

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
  ) {}

  submit(): void {
    this.errorMessage.set(null);
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        void this.router.navigate(['/tasks']);
      },
      error: (err) => {
        if (err.status === 401) {
          this.errorMessage.set('Неверный логин или пароль.');
        } else {
          this.errorMessage.set('Не удалось войти. Попробуйте позже.');
        }
      },
    });
  }
}
