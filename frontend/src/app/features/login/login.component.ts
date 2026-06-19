import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../core/auth/auth.service';

/** Login choreography :
 *    closed -> opening (door swings) -> revealing (duck reads 3s) -> handing (duck lowers paper 2.5s) -> open (form usable)
 *  Failed login does NOT reset the animation - only shakes the newspaper.
 */
type Stage = 'closed' | 'opening' | 'revealing' | 'handing' | 'open';

@Component({
  selector: 'fp-login',
  standalone: true,
  templateUrl: './login.component.pug',
  styleUrl: './login.component.scss',
  imports: [
    CommonModule, ReactiveFormsModule,
    MatButtonModule, MatFormFieldModule, MatInputModule,
    MatCheckboxModule, MatProgressSpinnerModule
  ]
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly stage = signal<Stage>('closed');
  readonly submitting = signal(false);
  readonly errorMsg = signal<string | null>(null);
  readonly shake = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
    rememberMe: [false]
  });

  openDoor(): void {
    if (this.stage() !== 'closed') return;
    this.stage.set('opening');
    setTimeout(() => this.stage.set('revealing'), 950);  // door fully open
    setTimeout(() => this.stage.set('handing'),  1050);  // duck skips the read, hands paper immediately
    setTimeout(() => this.stage.set('open'),     2500);  // 1.45s handing animation → form appears
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) return;
    this.submitting.set(true);
    this.errorMsg.set(null);
    const { username, password, rememberMe } = this.form.getRawValue();
    this.auth.login(username, password, rememberMe).subscribe({
      next: () => this.router.navigate(['/list']),
      error: err => {
        this.submitting.set(false);
        this.errorMsg.set(err.status === 401 ? 'Wrong username or password.' : 'Something broke. Try again.');
        this.shake.set(true);
        setTimeout(() => this.shake.set(false), 600);
      }
    });
  }
}
