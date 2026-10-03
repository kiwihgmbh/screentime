import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

/**
 * The shell. Mobile and tablet first: this is used on an iPad and a phone, so
 * the navigation is a row of icons at the bottom within reach of a thumb, and
 * the parent screens sit behind a menu rather than crowding the child's.
 */
@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
  ],
  styles: `
    :host {
      display: block;
      min-height: 100dvh;
      background: var(--mat-sys-surface);
    }
    .title {
      font-size: 1.05rem;
      font-weight: 500;
      flex: 1 1 auto;
    }
    main {
      max-width: 720px;
      margin: 0 auto;
      padding: 16px 16px calc(80px + env(safe-area-inset-bottom));
    }
    nav {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      display: flex;
      justify-content: space-around;
      padding: 6px 4px calc(6px + env(safe-area-inset-bottom));
      background: var(--mat-sys-surface-container);
      border-top: 1px solid var(--mat-sys-outline-variant);
      z-index: 10;
    }
    nav a {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
      padding: 6px 10px;
      border-radius: 10px;
      text-decoration: none;
      color: var(--mat-sys-on-surface-variant);
      font-size: 0.68rem;
      min-width: 56px;
    }
    nav a.active {
      color: var(--mat-sys-primary);
      background: var(--mat-sys-primary-container);
    }
  `,
  template: `
    @if (auth.signedIn()) {
      <mat-toolbar>
        <span class="title" i18n>Screentime</span>
        <button matIconButton [matMenuTriggerFor]="menu" [attr.aria-label]="'Menu'" i18n-aria-label>
          <mat-icon>more_vert</mat-icon>
        </button>
        <mat-menu #menu="matMenu">
          <div style="padding: 8px 16px; font-size: 0.85rem;">{{ auth.displayName() }}</div>
          @if (auth.isParent()) {
            <a mat-menu-item routerLink="/parent/week">
              <mat-icon>edit_calendar</mat-icon><span i18n>The week</span>
            </a>
            <a mat-menu-item routerLink="/parent/check">
              <mat-icon>fact_check</mat-icon><span i18n>The weekly check</span>
            </a>
            <a mat-menu-item routerLink="/parent/checklist">
              <mat-icon>checklist</mat-icon><span i18n>Checklist</span>
            </a>
            <a mat-menu-item routerLink="/parent/settings">
              <mat-icon>tune</mat-icon><span i18n>Settings</span>
            </a>
            <a mat-menu-item routerLink="/parent/users">
              <mat-icon>group</mat-icon><span i18n>Accounts</span>
            </a>
          }
          <button mat-menu-item (click)="auth.logout()">
            <mat-icon>logout</mat-icon><span i18n>Sign out</span>
          </button>
        </mat-menu>
      </mat-toolbar>
    }

    <main>
      <router-outlet />
    </main>

    @if (auth.signedIn()) {
      <nav>
        <a routerLink="/" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
          <mat-icon>schedule</mat-icon><span i18n>Now</span>
        </a>
        <a routerLink="/history" routerLinkActive="active">
          <mat-icon>history</mat-icon><span i18n>Weeks</span>
        </a>
        <a routerLink="/rules" routerLinkActive="active">
          <mat-icon>menu_book</mat-icon><span i18n>Rules</span>
        </a>
        @if (auth.isParent()) {
          <a routerLink="/parent/week" routerLinkActive="active">
            <mat-icon>edit_calendar</mat-icon><span i18n>Edit</span>
          </a>
          <a routerLink="/parent/check" routerLinkActive="active">
            <mat-icon>fact_check</mat-icon><span i18n>Check</span>
          </a>
        }
      </nav>
    }
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
}
