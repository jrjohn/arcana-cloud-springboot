import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '@services/auth.service';

interface NavItem {
  label: string;
  path: string;
  icon: string;
}

/**
 * Main Layout Component with sidebar navigation.
 */
@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <div class="layout">
      <!-- Sidebar -->
      <aside class="sidebar" [class.open]="sidebarOpen">
        <div class="sidebar-header">
          <a routerLink="/" class="logo">
            <div class="logo-icon">A</div>
            <span class="logo-text">Arcana</span>
          </a>
          <button class="close-btn" (click)="sidebarOpen = false">×</button>
        </div>

        <nav class="sidebar-nav">
          @for (item of navItems; track item.path) {
            <a
              [routerLink]="item.path"
              routerLinkActive="active"
              [routerLinkActiveOptions]="{ exact: item.path === '/' }"
              class="nav-item"
            >
              <span class="nav-icon">{{ item.icon }}</span>
              <span class="nav-label">{{ item.label }}</span>
            </a>
          }
        </nav>

        <div class="sidebar-footer">
          <button class="nav-item" (click)="logout()">
            <span class="nav-icon">🚪</span>
            <span class="nav-label">Sign out</span>
          </button>
        </div>
      </aside>

      <!-- Main Content -->
      <div class="main">
        <!-- Header -->
        <header class="header">
          <button class="menu-btn" (click)="sidebarOpen = true">☰</button>

          <div class="header-actions">
            <button class="theme-btn" (click)="toggleTheme()">
              {{ darkMode ? '☀️' : '🌙' }}
            </button>

            <div class="user-menu">
              <div class="user-avatar">
                {{ userInitial }}
              </div>
              <span class="user-name">{{ userName }}</span>
            </div>
          </div>
        </header>

        <!-- Page Content -->
        <main class="content">
          <ng-content />
        </main>
      </div>

      <!-- Mobile overlay -->
      @if (sidebarOpen) {
        <div class="overlay" (click)="sidebarOpen = false"></div>
      }
    </div>
  `,
  styles: [`
    .layout {
      display: flex;
      min-height: 100vh;
    }

    .sidebar {
      width: 256px;
      background: white;
      border-right: 1px solid #e5e7eb;
      display: flex;
      flex-direction: column;
      position: fixed;
      height: 100vh;
      z-index: 40;
    }

    .sidebar-header {
      height: 64px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 1rem;
      border-bottom: 1px solid #e5e7eb;
    }

    .logo {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      text-decoration: none;
    }

    .logo-icon {
      width: 32px;
      height: 32px;
      background: var(--arcana-accent);
      color: white;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: bold;
    }

    .logo-text {
      font-size: 1.25rem;
      font-weight: bold;
      color: #1f2937;
    }

    .close-btn {
      display: none;
      background: none;
      border: none;
      font-size: 1.5rem;
      cursor: pointer;
    }

    .sidebar-nav {
      flex: 1;
      padding: 1rem 0.5rem;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem 1rem;
      border-radius: 8px;
      text-decoration: none;
      color: #4b5563;
      transition: all 0.2s;
      cursor: pointer;
      border: none;
      background: none;
      width: 100%;
      text-align: left;
      font-size: 0.875rem;
    }

    .nav-item:hover {
      background: #f3f4f6;
    }

    .nav-item.active {
      background: var(--primary-50);
      color: var(--primary-600);
    }

    .nav-icon {
      font-size: 1.25rem;
    }

    .sidebar-footer {
      padding: 0.5rem;
      border-top: 1px solid #e5e7eb;
    }

    .main {
      flex: 1;
      margin-left: 256px;
      min-height: 100vh;
    }

    .header {
      height: 64px;
      background: white;
      border-bottom: 1px solid #e5e7eb;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 1.5rem;
      position: sticky;
      top: 0;
      z-index: 30;
    }

    .menu-btn {
      display: none;
      background: none;
      border: none;
      font-size: 1.5rem;
      cursor: pointer;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    .theme-btn {
      background: none;
      border: none;
      font-size: 1.25rem;
      cursor: pointer;
      padding: 0.5rem;
      border-radius: 8px;
    }

    .theme-btn:hover {
      background: #f3f4f6;
    }

    .user-menu {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .user-avatar {
      width: 32px;
      height: 32px;
      background: var(--primary-500);
      color: white;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 500;
    }

    .user-name {
      font-size: 0.875rem;
      font-weight: 500;
    }

    .content {
      padding: 1.5rem;
    }

    .overlay {
      display: none;
    }

    @media (max-width: 1024px) {
      .sidebar {
        transform: translateX(-100%);
        transition: transform 0.3s;
      }

      .sidebar.open {
        transform: translateX(0);
      }

      .close-btn {
        display: block;
      }

      .main {
        margin-left: 0;
      }

      .menu-btn {
        display: block;
      }

      .overlay {
        display: block;
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.5);
        z-index: 30;
      }

      .user-name {
        display: none;
      }
    }
  `],
})
export class LayoutComponent {
  private readonly authService = inject(AuthService);

  sidebarOpen = false;
  darkMode = false;

  readonly navItems: NavItem[] = [
    { label: 'Dashboard', path: '/', icon: '🏠' },
    { label: 'Plugins', path: '/plugins', icon: '🧩' },
    { label: 'Users', path: '/users', icon: '👥' },
    { label: 'Audit Logs', path: '/audit', icon: '📋' },
    { label: 'Settings', path: '/settings', icon: '⚙️' },
  ];

  get userName(): string {
    const user = this.authService.getCurrentUser();
    return user?.username || 'Admin';
  }

  get userInitial(): string {
    return this.userName.charAt(0).toUpperCase();
  }

  toggleTheme(): void {
    this.darkMode = !this.darkMode;
    document.documentElement.classList.toggle('dark', this.darkMode);
  }

  logout(): void {
    this.authService.logout();
  }
}
